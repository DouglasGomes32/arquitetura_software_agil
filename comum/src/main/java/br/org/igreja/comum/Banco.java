package br.org.igreja.comum;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Cada serviço recebe somente o caminho do próprio arquivo H2. */
public final class Banco {
    private final String url;
    public Banco(Path arquivo) throws Exception {
        var absoluto = arquivo.toAbsolutePath().normalize();
        Files.createDirectories(absoluto.getParent());
        url = "jdbc:h2:file:" + absoluto.toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
    }
    public Connection abrir() throws SQLException { return DriverManager.getConnection(url, "sa", ""); }
    public void executar(String... comandos) throws SQLException {
        try (var c = abrir(); var s = c.createStatement()) {
            for (var comando : comandos) s.execute(comando);
        }
    }
}
