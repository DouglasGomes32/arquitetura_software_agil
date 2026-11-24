package br.org.igreja;

import br.org.igreja.avisos.AvisosBanco;
import br.org.igreja.comum.Banco;
import br.org.igreja.comum.Contrato.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TransacaoTest {
    @TempDir Path pasta;
    private EscalaPublicada evento() {
        return new EscalaPublicada(1, "EscalaPublicada", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "2026-11-13T12:00:00Z", "Culto", "2026-11-22", List.of(new Voluntario("ana", "Ana"), new Voluntario("joao", "João")));
    }
    @Test void erroNoSegundoAvisoReverteTambemPrimeiroAvisoEMarcador() throws Exception {
        var arquivo = new Banco(pasta.resolve("avisos/banco"));
        var avisos = new AvisosBanco(arquivo);
        var evento = evento();
        // Injeta falha de banco depois que o marcador e o primeiro aviso foram inseridos.
        arquivo.executar("ALTER TABLE avisos ADD CONSTRAINT falha_teste CHECK (voluntario_id <> 'joao')");
        assertThrows(SQLException.class, () -> avisos.processar(evento));
        assertEquals(0, avisos.contarEventos());
        assertTrue(avisos.consultar("ana").isEmpty());
        arquivo.executar("ALTER TABLE avisos DROP CONSTRAINT falha_teste");
        assertTrue(avisos.processar(evento), "A mesma mensagem deve funcionar depois da correção");
        assertEquals(1, avisos.contarEventos());
        assertEquals(1, avisos.consultar("ana").size());
        assertEquals(1, avisos.consultar("joao").size());
    }
    @Test void mensagensConcorrentesSoGravamUmConjuntoDeAvisos() throws Exception {
        var arquivo = new Banco(pasta.resolve("avisos/banco"));
        var avisos = new AvisosBanco(arquivo);
        var evento = evento();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tarefas = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) tarefas.add(executor.submit(() -> avisos.processar(evento)));
            int gravacoes = 0;
            for (var tarefa : tarefas) if (tarefa.get()) gravacoes++;
            assertEquals(1, gravacoes);
        }
        assertEquals(1, avisos.contarEventos());
        assertEquals(1, avisos.consultar("ana").size());
        assertEquals(1, avisos.consultar("joao").size());
    }
}
