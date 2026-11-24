package br.org.igreja;

import br.org.igreja.avisos.AvisosApp;
import br.org.igreja.broker.BrokerApp;
import br.org.igreja.comum.*;
import br.org.igreja.comum.Contrato.*;
import br.org.igreja.escalas.EscalasApp;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static br.org.igreja.comum.Contrato.JSON;
import static org.junit.jupiter.api.Assertions.*;

class FluxoTest {
    @TempDir Path pasta;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private record Resposta(int status, String corpo, String cache) {}
    private Resposta chamar(int porta, String metodo, String caminho, Object corpo) throws Exception {
        var requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho)).timeout(Duration.ofSeconds(5));
        requisicao.header("Content-Type", "application/json");
        requisicao.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.toJson(corpo)));
        var resposta = http.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
        return new Resposta(resposta.statusCode(), resposta.body(), resposta.headers().firstValue("X-Cache").orElse(""));
    }
    private static void aguardar(BooleanSupplier condicao) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) { if (condicao.getAsBoolean()) return; Thread.sleep(100); }
        fail("Condição não atingida em 15 segundos");
    }
    private String url(BrokerService broker) throws Exception { return broker.getTransportConnectors().getFirst().getPublishableConnectString(); }
    private int quantidade(int porta, String id) {
        try { return JSON.fromJson(chamar(porta, "GET", "/avisos?voluntarioId=" + id, null).corpo(), Aviso[].class).length; }
        catch (Exception e) { return -1; }
    }
    private long metrica(int porta, String nome) {
        try { return JSON.fromJson(chamar(porta, "GET", "/health", null).corpo(), com.google.gson.JsonObject.class).get(nome).getAsLong(); }
        catch (Exception e) { return -1; }
    }
    private Escala criar(int porta, String nome) throws Exception {
        var resposta = chamar(porta, "POST", "/escalas", new NovaEscala(nome, "2026-11-22", List.of(new Voluntario("ana", "Ana"), new Voluntario("joao", "João"))));
        assertEquals(201, resposta.status(), resposta.corpo());
        return JSON.fromJson(resposta.corpo(), Escala.class);
    }

    @Test void publicaPelaFilaInvalidaCacheEDeduplicaMesmoDepoisDeReiniciar() throws Exception {
        var broker = BrokerApp.iniciar(pasta.resolve("broker"), 0);
        try (var escalas = new EscalasApp(pasta.resolve("escalas/banco"), url(broker), 0)) {
            escalas.iniciar();
            String escalaId;
            try (var avisos = new AvisosApp(pasta.resolve("avisos/banco"), url(broker), 0, Duration.ofSeconds(60))) {
                avisos.iniciar();
                assertEquals("MISS", chamar(avisos.porta(), "GET", "/avisos?voluntarioId=ana", null).cache());
                assertEquals("HIT", chamar(avisos.porta(), "GET", "/avisos?voluntarioId=ana", null).cache());
                var escala = criar(escalas.porta(), "Culto da noite");
                escalaId = escala.id();
                var publicado = chamar(escalas.porta(), "POST", "/escalas/" + escala.id() + "/publicar", null);
                assertEquals(202, publicado.status());
                String eventoId = JSON.fromJson(publicado.corpo(), Escala.class).eventoId();
                var repetido = chamar(escalas.porta(), "POST", "/escalas/" + escala.id() + "/publicar", null);
                assertEquals(eventoId, JSON.fromJson(repetido.corpo(), Escala.class).eventoId());
                aguardar(() -> metrica(avisos.porta(), "eventosProcessados") == 1);
                var primeira = chamar(avisos.porta(), "GET", "/avisos?voluntarioId=ana", null);
                assertEquals("MISS", primeira.cache(), "Novo evento precisa invalidar o resultado vazio");
                assertEquals(1, quantidade(avisos.porta(), "ana"));
                assertEquals(1, quantidade(avisos.porta(), "joao"));
                assertEquals(0, quantidade(avisos.porta(), "maria"));
                assertEquals(202, chamar(escalas.porta(), "POST", "/escalas/" + escala.id() + "/reentregar", null).status());
                aguardar(() -> metrica(avisos.porta(), "duplicadosIgnorados") >= 1);
                assertEquals(1, quantidade(avisos.porta(), "ana"));
            }
            try (var avisos = new AvisosApp(pasta.resolve("avisos/banco"), url(broker), 0, Duration.ofSeconds(60))) {
                avisos.iniciar();
                // Nova entrega física do mesmo evento depois de reiniciar o consumidor.
                assertEquals(202, chamar(escalas.porta(), "POST", "/escalas/" + escalaId + "/reentregar", null).status());
                aguardar(() -> metrica(avisos.porta(), "duplicadosIgnorados") == 1);
                assertEquals(1, metrica(avisos.porta(), "eventosProcessados"));
                assertEquals(1, quantidade(avisos.porta(), "ana"));
            }
        } finally { broker.stop(); broker.waitUntilStopped(); }
    }

    @Test void outboxSobreviveProdutorReiniciadoEFilaGuardaMensagemSemConsumidor() throws Exception {
        String escalaId;
        // A fila está realmente desligada, não simulada por mocks.
        try (var escalas = new EscalasApp(pasta.resolve("escalas/banco"), "tcp://127.0.0.1:0", 0)) {
            escalas.iniciar();
            escalaId = criar(escalas.porta(), "Culto da manhã").id();
            assertEquals(202, chamar(escalas.porta(), "POST", "/escalas/" + escalaId + "/publicar", null).status());
            assertEquals(1, metrica(escalas.porta(), "outboxPendentes"));
        }
        var broker = BrokerApp.iniciar(pasta.resolve("broker"), 0);
        int portaBroker = broker.getTransportConnectors().getFirst().getConnectUri().getPort();
        try {
            try (var escalas = new EscalasApp(pasta.resolve("escalas/banco"), url(broker), 0)) {
                escalas.iniciar();
                assertEquals("PUBLICADA", JSON.fromJson(chamar(escalas.porta(), "GET", "/escalas/" + escalaId, null).corpo(), Escala.class).status());
                aguardar(() -> metrica(escalas.porta(), "outboxPendentes") == 0);
            }
        } finally { broker.stop(); broker.waitUntilStopped(); }
        // Reinicia também o broker: a mensagem deve voltar do armazenamento KahaDB.
        broker = BrokerApp.iniciar(pasta.resolve("broker"), portaBroker);
        try (var avisos = new AvisosApp(pasta.resolve("avisos/banco"), url(broker), 0, Duration.ofSeconds(60))) {
            avisos.iniciar();
            aguardar(() -> quantidade(avisos.porta(), "ana") == 1);
            assertEquals(1, quantidade(avisos.porta(), "joao"));
        } finally { broker.stop(); broker.waitUntilStopped(); }
    }

    @Test void rejeitaEntradaInvalidaEMensagemEnvenenadaVaiParaDlq() throws Exception {
        var broker = BrokerApp.iniciar(pasta.resolve("broker"), 0);
        try (var escalas = new EscalasApp(pasta.resolve("escalas/banco"), url(broker), 0);
             var avisos = new AvisosApp(pasta.resolve("avisos/banco"), url(broker), 0, Duration.ofSeconds(60))) {
            escalas.iniciar(); avisos.iniciar();
            assertEquals(400, chamar(escalas.porta(), "POST", "/escalas", new NovaEscala("Culto", "2026-02-30", List.of())).status());
            assertEquals(400, chamar(escalas.porta(), "POST", "/escalas", new NovaEscala("Culto", "2026-11-22", List.of(new Voluntario("ana", "Ana"), new Voluntario("ana", "Ana")))).status());
            assertEquals(400, chamar(avisos.porta(), "GET", "/avisos", null).status());
            assertEquals(404, chamar(escalas.porta(), "POST", "/escalas/inexistente/publicar", null).status());
            enviar(url(broker), "{json quebrado");
            try (var c = Fila.fabrica(url(broker)).createConnection(); var s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 var dlq = s.createConsumer(s.createQueue("ActiveMQ.DLQ"))) {
                c.start(); assertNotNull(dlq.receive(15000), "Após 3 reentregas o evento deve ir para a DLQ");
            }
            assertEquals(0, metrica(avisos.porta(), "eventosProcessados"));
            var valida = criar(escalas.porta(), "Culto após DLQ");
            chamar(escalas.porta(), "POST", "/escalas/" + valida.id() + "/publicar", null);
            aguardar(() -> quantidade(avisos.porta(), "ana") == 1);
        } finally { broker.stop(); broker.waitUntilStopped(); }
    }

    @Test void publicacoesConcorrentesGeramUmUnicoEvento() throws Exception {
        try (var escalas = new EscalasApp(pasta.resolve("escalas/banco"), "tcp://127.0.0.1:0", 0)) {
            escalas.iniciar();
            var escala = criar(escalas.porta(), "Culto concorrente");
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var chamadas = new ArrayList<java.util.concurrent.Future<String>>();
                for (int i = 0; i < 8; i++) chamadas.add(executor.submit(() -> {
                    var r = chamar(escalas.porta(), "POST", "/escalas/" + escala.id() + "/publicar", null);
                    assertEquals(202, r.status());
                    return JSON.fromJson(r.corpo(), Escala.class).eventoId();
                }));
                var ids = new java.util.HashSet<String>();
                for (var c : chamadas) ids.add(c.get());
                assertEquals(1, ids.size());
                assertEquals(1, metrica(escalas.porta(), "outboxPendentes"));
            }
        }
    }
    private void enviar(String url, String payload) throws Exception {
        try (var c = Fila.fabrica(url).createConnection(); var s = c.createSession(true, Session.SESSION_TRANSACTED);
             var p = s.createProducer(s.createQueue(Contrato.FILA))) {
            p.setDeliveryMode(DeliveryMode.PERSISTENT);
            p.send(s.createTextMessage(payload)); s.commit();
        }
    }
}
