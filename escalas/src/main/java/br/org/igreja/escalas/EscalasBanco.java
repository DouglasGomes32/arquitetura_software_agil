package br.org.igreja.escalas;

import br.org.igreja.comum.Banco;
import br.org.igreja.comum.Contrato;
import br.org.igreja.comum.Contrato.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static br.org.igreja.comum.Contrato.JSON;

public final class EscalasBanco {
    private final Banco banco;
    public record Pendente(String id, String payload) {}
    public EscalasBanco(Banco banco) throws SQLException {
        this.banco = banco;
        banco.executar("""
            CREATE TABLE IF NOT EXISTS escalas (
              id VARCHAR(36) PRIMARY KEY, evento VARCHAR(120) NOT NULL, data_evento VARCHAR(10) NOT NULL,
              voluntarios CLOB NOT NULL, evento_id VARCHAR(36), status VARCHAR(20) NOT NULL)
            """, """
            CREATE TABLE IF NOT EXISTS outbox (
              id VARCHAR(36) PRIMARY KEY, escala_id VARCHAR(36) NOT NULL REFERENCES escalas(id),
              payload CLOB NOT NULL, enviado BOOLEAN NOT NULL DEFAULT FALSE)
            """, "CREATE INDEX IF NOT EXISTS idx_outbox_pendente ON outbox(enviado)");
    }
    public Escala criar(NovaEscala nova) throws SQLException {
        Contrato.validar(nova);
        String id = UUID.randomUUID().toString();
        try (var c = banco.abrir(); var s = c.prepareStatement("INSERT INTO escalas VALUES (?, ?, ?, ?, NULL, 'RASCUNHO')")) {
            s.setString(1, id); s.setString(2, nova.evento()); s.setString(3, nova.data());
            s.setString(4, JSON.toJson(nova.voluntarios())); s.executeUpdate();
        }
        return buscar(id);
    }
    public Escala buscar(String id) throws SQLException {
        try (var c = banco.abrir()) { return buscar(c, id, false); }
    }
    public List<Escala> listar() throws SQLException {
        try (var c = banco.abrir(); var s = c.createStatement();
             var r = s.executeQuery("SELECT id FROM escalas ORDER BY data_evento DESC, id DESC FETCH FIRST 100 ROWS ONLY")) {
            var escalas = new ArrayList<Escala>();
            while (r.next()) escalas.add(buscar(c, r.getString(1), false));
            return escalas;
        }
    }
    private Escala buscar(Connection c, String id, boolean bloquear) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM escalas WHERE id=?" + (bloquear ? " FOR UPDATE" : ""))) {
            s.setString(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next()) return null;
                return new Escala(id, r.getString("evento"), r.getString("data_evento"),
                    List.of(JSON.fromJson(r.getString("voluntarios"), Voluntario[].class)), r.getString("status"), r.getString("evento_id"));
            }
        }
    }
    /** Publicação e outbox compartilham uma única transação local. */
    public Escala publicar(String id) throws SQLException {
        try (var c = banco.abrir()) {
            c.setAutoCommit(false);
            try {
                var escala = buscar(c, id, true);
                if (escala == null || "PUBLICADA".equals(escala.status())) { c.commit(); return escala; }
                String eventoId = UUID.randomUUID().toString();
                var evento = new EscalaPublicada(1, "EscalaPublicada", eventoId, id, Instant.now().toString(), escala.evento(), escala.data(), escala.voluntarios());
                inserirOutbox(c, id, JSON.toJson(evento));
                try (var s = c.prepareStatement("UPDATE escalas SET status='PUBLICADA', evento_id=? WHERE id=?")) {
                    s.setString(1, eventoId); s.setString(2, id); s.executeUpdate();
                }
                c.commit();
                return new Escala(id, escala.evento(), escala.data(), escala.voluntarios(), "PUBLICADA", eventoId);
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
    /** Ferramenta da demonstração: repete exatamente o evento original, inclusive eventoId. */
    public boolean reentregar(String id) throws SQLException {
        try (var c = banco.abrir()) {
            c.setAutoCommit(false);
            try (var s = c.prepareStatement("SELECT payload FROM outbox WHERE escala_id=? FETCH FIRST 1 ROW ONLY")) {
                s.setString(1, id);
                try (var r = s.executeQuery()) {
                    if (!r.next()) { c.rollback(); return false; }
                    inserirOutbox(c, id, r.getString(1));
                }
                c.commit(); return true;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
    private void inserirOutbox(Connection c, String id, String payload) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO outbox(id, escala_id, payload) VALUES (?, ?, ?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, id); s.setString(3, payload); s.executeUpdate();
        }
    }
    public List<Pendente> pendentes() throws SQLException {
        try (var c = banco.abrir(); var s = c.createStatement(); var r = s.executeQuery("SELECT id, payload FROM outbox WHERE enviado=FALSE FETCH FIRST 50 ROWS ONLY")) {
            var itens = new ArrayList<Pendente>();
            while (r.next()) itens.add(new Pendente(r.getString(1), r.getString(2)));
            return itens;
        }
    }
    public void marcarEnviado(String id) throws SQLException {
        try (var c = banco.abrir(); var s = c.prepareStatement("UPDATE outbox SET enviado=TRUE WHERE id=?")) {
            s.setString(1, id); s.executeUpdate();
        }
    }
}
