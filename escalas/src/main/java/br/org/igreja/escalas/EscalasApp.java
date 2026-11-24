package br.org.igreja.escalas;

import br.org.igreja.comum.*;
import br.org.igreja.comum.Contrato.NovaEscala;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EscalasApp implements AutoCloseable {
    private final EscalasBanco banco;
    private final Http http;
    private final String brokerUrl;
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor();
    public EscalasApp(Path arquivo, String brokerUrl, int porta) throws Exception {
        banco = new EscalasBanco(new Banco(arquivo));
        this.brokerUrl = brokerUrl;
        http = new Http(porta, e -> {
            String caminho = e.getRequestURI().getPath();
            String metodo = e.getRequestMethod();
            if ("GET".equals(metodo) && ("/".equals(caminho) || "/painel".equals(caminho))) {
                Http.recurso(e, EscalasApp.class, "/painel/index.html", "text/html; charset=utf-8");
            } else if ("GET".equals(metodo) && "/painel/app.css".equals(caminho)) {
                Http.recurso(e, EscalasApp.class, "/painel/app.css", "text/css; charset=utf-8");
            } else if ("GET".equals(metodo) && "/painel/app.js".equals(caminho)) {
                Http.recurso(e, EscalasApp.class, "/painel/app.js", "text/javascript; charset=utf-8");
            } else if ("GET".equals(metodo) && "/painel/config".equals(caminho)) {
                Http.responder(e, 200, Map.of("avisosUrl", System.getProperty("avisos.url", "http://127.0.0.1:8082")));
            } else if ("GET".equals(metodo) && "/escalas".equals(caminho)) {
                Http.responder(e, 200, banco.listar());
            } else if ("/health".equals(caminho) && "GET".equals(metodo)) {
                Http.responder(e, 200, Map.of("servico", "escalas", "status", "UP", "outboxPendentes", banco.pendentes().size()));
            } else if ("/escalas".equals(caminho) && "POST".equals(metodo)) {
                var escala = banco.criar(Http.corpo(e, NovaEscala.class));
                e.getResponseHeaders().set("Location", "/escalas/" + escala.id());
                Http.responder(e, 201, escala);
            } else {
                var partes = caminho.split("/");
                if (partes.length < 3 || !"escalas".equals(partes[1])) { Http.naoEncontrado(e); return; }
                if (partes.length == 3 && "GET".equals(metodo)) {
                    var escala = banco.buscar(partes[2]);
                    if (escala == null) Http.naoEncontrado(e); else Http.responder(e, 200, escala);
                } else if (partes.length == 4 && "publicar".equals(partes[3]) && "POST".equals(metodo)) {
                    var escala = banco.publicar(partes[2]);
                    if (escala == null) Http.naoEncontrado(e); else Http.responder(e, 202, escala);
                } else if (partes.length == 4 && "reentregar".equals(partes[3]) && "POST".equals(metodo)) {
                    if (banco.reentregar(partes[2])) Http.responder(e, 202, Map.of("status", "Reentrega agendada"));
                    else Http.responder(e, 409, Map.of("erro", "Escala inexistente ou ainda não publicada"));
                } else Http.naoEncontrado(e);
            }
        });
    }
    public void iniciar() {
        http.iniciar();
        dispatcher.scheduleWithFixedDelay(this::enviarPendentes, 0, 500, TimeUnit.MILLISECONDS);
    }
    public int porta() { return http.porta(); }
    private void enviarPendentes() {
        try {
            var pendentes = banco.pendentes();
            if (pendentes.isEmpty()) return;
            try (var conexao = Fila.fabrica(brokerUrl).createConnection();
                 var sessao = conexao.createSession(true, Session.SESSION_TRANSACTED);
                 var produtor = sessao.createProducer(sessao.createQueue(Contrato.FILA))) {
                produtor.setDeliveryMode(DeliveryMode.PERSISTENT);
                for (var item : pendentes) {
                    produtor.send(sessao.createTextMessage(item.payload()));
                    sessao.commit(); // Só marca a outbox depois da confirmação do broker.
                    banco.marcarEnviado(item.id());
                }
            }
        } catch (Exception e) { System.err.println("Outbox preservada; nova tentativa em 500 ms: " + e.getMessage()); }
    }
    @Override public void close() {
        http.close();
        // Não interromper operações de arquivo do H2 no meio de um commit.
        dispatcher.shutdown();
        try { dispatcher.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    public static void main(String[] args) throws Exception {
        var app = new EscalasApp(Path.of(System.getProperty("escalas.db", "data/escalas/banco")), System.getProperty("broker.url", "tcp://127.0.0.1:61616"), Integer.getInteger("http.port", 8081));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.iniciar();
        System.out.println("Serviço Escalas: http://127.0.0.1:" + app.porta());
        new CountDownLatch(1).await();
    }
}
