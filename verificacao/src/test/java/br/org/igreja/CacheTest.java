package br.org.igreja;

import br.org.igreja.avisos.CacheAvisos;
import br.org.igreja.comum.Contrato.Aviso;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CacheTest {
    @Test void expiraInvalidaIsolaVoluntariosELimitaMemoria() throws Exception {
        var tempo = new AtomicLong();
        var consultas = new AtomicInteger();
        var cache = new CacheAvisos(Duration.ofSeconds(60), 2, tempo::get);
        CacheAvisos.Consulta fonte = () -> { consultas.incrementAndGet(); return List.<Aviso>of(); };
        assertEquals("MISS", cache.consultar("ana", fonte).origem());
        assertEquals("HIT", cache.consultar("ana", fonte).origem());
        assertEquals("MISS", cache.consultar("joao", fonte).origem());
        cache.invalidar("ana");
        assertEquals("MISS", cache.consultar("ana", fonte).origem());
        assertEquals("HIT", cache.consultar("joao", fonte).origem());
        tempo.set(Duration.ofSeconds(60).toNanos());
        assertEquals("MISS", cache.consultar("ana", fonte).origem());
        assertEquals(4, consultas.get());
        cache.consultar("joao", fonte);
        cache.consultar("maria", fonte);
        assertEquals("MISS", cache.consultar("ana", fonte).origem());
    }
}
