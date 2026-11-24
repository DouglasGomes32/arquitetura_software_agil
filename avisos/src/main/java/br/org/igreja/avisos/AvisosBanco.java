package br.org.igreja.avisos;

import br.org.igreja.comum.Banco;
import br.org.igreja.comum.Contrato;
import br.org.igreja.comum.Contrato.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AvisosBanco {
    private final Banco banco;
    public AvisosBanco(Banco banco) throws SQLException {
        this.banco = banco;
        banco.executar("""
            CREATE TABLE IF NOT EXISTS eventos_processados (
              evento_id VARCHAR(36) PRIMARY KEY, processado_em VARCHAR(40) NOT NULL)
            """, """
            CREATE TABLE IF NOT EXISTS avisos (
              id VARCHAR(36) PRIMARY KEY, evento_id VARCHAR(36) NOT NULL REFERENCES eventos_processados(evento_id),
              escala_id VARCHAR(36) NOT NULL, voluntario_id VARCHAR(64) NOT NULL,
              voluntario_nome VARCHAR(120) NOT NULL, mensagem VARCHAR(500) NOT NULL, criado_em VARCHAR(40) NOT NULL,
              UNIQUE(evento_id, voluntario_id))
            """, "CREATE INDEX IF NOT EXISTS idx_avisos_voluntario ON avisos(voluntario_id)");
    }
    /** O registro de idempotência e todos os avisos são confirmados juntos. */
    public boolean processar(EscalaPublicada evento) throws SQLException {
        Contrato.validar(evento);
        try (var c = banco.abrir()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("INSERT INTO eventos_processados VALUES (?, ?)")) {
                    s.setString(1, evento.eventoId()); s.setString(2, Instant.now().toString());
                    try { s.executeUpdate(); }
                    catch (SQLException e) {
                        c.rollback();
                        if ("23505".equals(e.getSQLState())) return false;
                        throw e;
                    }
                }
                try (var s = c.prepareStatement("INSERT INTO avisos VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (var voluntario : evento.voluntarios()) {
                        s.setString(1, UUID.randomUUID().toString()); s.setString(2, evento.eventoId()); s.setString(3, evento.escalaId());
                        s.setString(4, voluntario.id()); s.setString(5, voluntario.nome());
                        s.setString(6, voluntario.nome() + ", você está na escala de " + evento.evento() + " em " + evento.data() + ".");
                        s.setString(7, Instant.now().toString()); s.addBatch();
                    }
                    s.executeBatch();
                }
                c.commit(); return true;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
    public List<Aviso> consultar(String voluntarioId) throws SQLException {
        try (var c = banco.abrir(); var s = c.prepareStatement("SELECT * FROM avisos WHERE voluntario_id=? ORDER BY criado_em, id")) {
            s.setString(1, voluntarioId);
            try (var r = s.executeQuery()) {
                var avisos = new ArrayList<Aviso>();
                while (r.next()) avisos.add(new Aviso(r.getString("id"), r.getString("evento_id"), r.getString("escala_id"), r.getString("voluntario_id"), r.getString("voluntario_nome"), r.getString("mensagem"), r.getString("criado_em")));
                return avisos;
            }
        }
    }
    public long contarEventos() throws SQLException {
        try (var c = banco.abrir(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM eventos_processados")) {
            r.next(); return r.getLong(1);
        }
    }
}
