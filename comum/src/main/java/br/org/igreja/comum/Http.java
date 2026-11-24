package br.org.igreja.comum;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static br.org.igreja.comum.Contrato.JSON;

public final class Http implements AutoCloseable {
    @FunctionalInterface public interface Rota { void executar(HttpExchange e) throws Exception; }
    private final HttpServer servidor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    public Http(int porta, Rota rota) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 0);
        servidor.setExecutor(executor);
        servidor.createContext("/", e -> {
            try (e) {
                try { rota.executar(e); }
                catch (IllegalArgumentException | JsonParseException | DateTimeException ex) {
                    responder(e, 400, Map.of("erro", "Entrada inválida: " + ex.getMessage()));
                } catch (Exception ex) {
                    System.err.println("Falha HTTP: " + ex);
                    responder(e, 500, Map.of("erro", "Falha interna; consulte o log"));
                }
            }
        });
    }
    public void iniciar() { servidor.start(); }
    public int porta() { return servidor.getAddress().getPort(); }
    public static <T> T corpo(HttpExchange e, Class<T> tipo) throws IOException {
        byte[] bytes = e.getRequestBody().readNBytes(65537);
        if (bytes.length > 65536) throw new IllegalArgumentException("Corpo excede 64 KiB");
        return JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), tipo);
    }
    public static String parametro(HttpExchange e, String chave) {
        String query = e.getRequestURI().getRawQuery();
        if (query != null) for (String par : query.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes[0].equals(chave)) return URLDecoder.decode(partes.length == 2 ? partes[1] : "", StandardCharsets.UTF_8);
        }
        return null;
    }
    public static void responder(HttpExchange e, int status, Object valor) throws IOException {
        byte[] bytes = JSON.toJson(valor).getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
    }
    public static void naoEncontrado(HttpExchange e) throws IOException { responder(e, 404, Map.of("erro", "Recurso não encontrado")); }
    public static void recurso(HttpExchange e, Class<?> origem, String caminho, String tipo) throws IOException {
        try (var arquivo = origem.getResourceAsStream(caminho)) {
            if (arquivo == null) { naoEncontrado(e); return; }
            byte[] bytes = arquivo.readAllBytes();
            e.getResponseHeaders().set("Content-Type", tipo);
            e.getResponseHeaders().set("Cache-Control", "no-store");
            e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            e.sendResponseHeaders(200, bytes.length);
            e.getResponseBody().write(bytes);
        }
    }
    /** Só o navegador do painel local pode ler a API Avisos entre origens. */
    public static void permitirLeituraDoPainel(HttpExchange e) {
        String origem = e.getRequestHeaders().getFirst("Origin");
        String permitida = System.getProperty("painel.origin", "http://127.0.0.1:8081");
        if (permitida.equals(origem) || permitida.replace("127.0.0.1", "localhost").equals(origem)) {
            e.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            e.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Cache");
        }
        e.getResponseHeaders().set("Vary", "Origin");
    }
    @Override public void close() { servidor.stop(0); executor.close(); }
}
