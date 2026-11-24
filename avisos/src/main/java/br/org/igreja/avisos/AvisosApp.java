package br.org.igreja.avisos;

import br.org.igreja.comum.*;
import br.org.igreja.comum.Contrato.EscalaPublicada;
import jakarta.jms.Connection;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import static br.org.igreja.comum.Contrato.JSON;

public final class AvisosApp implements AutoCloseable {
    private final AvisosBanco banco;
    private final CacheAvisos cache;
    private final Http http;
    private final String brokerUrl;
    private final Thread consumidor;
    private volatile boolean ativo = true;
    private volatile boolean conectado;
    private volatile Connection conexaoAtual;
    private final AtomicLong duplicados = new AtomicLong();
    public AvisosApp(Path arquivo, String brokerUrl, int porta, Duration ttl) throws Exception {
        banco = new AvisosBanco(new Banco(arquivo));
        cache = new CacheAvisos(ttl, 1000, System::nanoTime);
        this.brokerUrl = brokerUrl;
        http = new Http(porta, e -> {
            if ("GET".equals(e.getRequestMethod())) Http.permitirLeituraDoPainel(e);
            String caminho = e.getRequestURI().getPath();
            if ("/health".equals(caminho) && "GET".equals(e.getRequestMethod())) {
                Http.responder(e, 200, Map.of("servico", "avisos", "status", "UP", "filaConectada", conectado, "eventosProcessados", banco.contarEventos(), "duplicadosIgnorados", duplicados.get()));
            } else if ("/avisos".equals(caminho) && "GET".equals(e.getRequestMethod())) {
                String id = Contrato.texto(Http.parametro(e, "voluntarioId"), 64, "voluntarioId");
                var resultado = cache.consultar(id, () -> banco.consultar(id));
                e.getResponseHeaders().set("X-Cache", resultado.origem());
                Http.responder(e, 200, resultado.avisos());
            } else Http.naoEncontrado(e);
        });
        consumidor = new Thread(this::consumir, "consumidor-avisos");
    }
    public void iniciar() { http.iniciar(); consumidor.start(); }
    public int porta() { return http.porta(); }
    private void consumir() {
        while (ativo) {
            try (var conexao = Fila.fabrica(brokerUrl).createConnection()) {
                conexaoAtual = conexao;
                try (var sessao = conexao.createSession(true, Session.SESSION_TRANSACTED);
                     var receptor = sessao.createConsumer(sessao.createQueue(Contrato.FILA))) {
                    conexao.start(); conectado = true;
                    while (ativo) {
                        var mensagem = receptor.receive(500);
                        if (mensagem == null) continue;
                        try {
                            if (!(mensagem instanceof TextMessage texto)) throw new IllegalArgumentException("Esperado JSON em TextMessage");
                            var evento = JSON.fromJson(texto.getText(), EscalaPublicada.class);
                            // Impede que uma consulta antiga preencha o cache após a invalidação.
                            synchronized (cache) {
                                if (banco.processar(evento)) {
                                    for (var voluntario : evento.voluntarios()) cache.invalidar(voluntario.id());
                                } else duplicados.incrementAndGet();
                            }
                            sessao.commit(); // ACK só depois do commit no banco e invalidação.
                        } catch (Exception e) {
                            System.err.println("Mensagem não confirmada; retry/DLQ: " + e.getMessage());
                            sessao.rollback();
                        }
                    }
                }
            } catch (Exception e) {
                if (ativo) System.err.println("Consumidor aguardando fila: " + e.getMessage());
            } finally { conectado = false; conexaoAtual = null; }
            if (ativo) try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }
    @Override public void close() {
        ativo = false;
        http.close();
        try {
            // Permite terminar um commit H2 antes de fechar a sessão JMS.
            consumidor.join(5000);
            if (consumidor.isAlive()) {
                var conexao = conexaoAtual;
                if (conexao != null) try { conexao.close(); } catch (Exception ignored) { /* A recepção pode já ter fechado. */ }
                consumidor.join(5000);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    public static void main(String[] args) throws Exception {
        var app = new AvisosApp(Path.of(System.getProperty("avisos.db", "data/avisos/banco")), System.getProperty("broker.url", "tcp://127.0.0.1:61616"), Integer.getInteger("http.port", 8082), Duration.ofSeconds(Long.getLong("cache.ttl.seconds", 60)));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.iniciar();
        System.out.println("Serviço Avisos: http://127.0.0.1:" + app.porta());
        new CountDownLatch(1).await();
    }
}
