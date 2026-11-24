package br.org.igreja.avisos;

import br.org.igreja.comum.Contrato.Aviso;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongSupplier;

/** Cache local limitado. A mesma trava cobre consulta, commit e invalidação. */
public final class CacheAvisos {
    @FunctionalInterface public interface Consulta { List<Aviso> executar() throws Exception; }
    public record Resultado(List<Aviso> avisos, String origem) {}
    private record Entrada(List<Aviso> avisos, long expiraEm) {}
    private final LinkedHashMap<String, Entrada> entradas = new LinkedHashMap<>(16, 0.75f, true);
    private final long ttlNanos;
    private final LongSupplier relogio;
    private final int limite;
    public CacheAvisos(Duration ttl, int limite, LongSupplier relogio) {
        if (ttl.isNegative() || ttl.isZero() || limite < 1) throw new IllegalArgumentException("TTL e limite devem ser positivos");
        ttlNanos = ttl.toNanos(); this.limite = limite; this.relogio = relogio;
    }
    public synchronized Resultado consultar(String voluntarioId, Consulta consulta) throws Exception {
        long agora = relogio.getAsLong();
        // Remove também chaves expiradas que não voltaram a ser consultadas.
        entradas.entrySet().removeIf(e -> agora - e.getValue().expiraEm() >= 0);
        var entrada = entradas.get(voluntarioId);
        if (entrada != null) return new Resultado(entrada.avisos(), "HIT");
        var avisos = List.copyOf(consulta.executar());
        if (entradas.size() >= limite) entradas.remove(entradas.keySet().iterator().next());
        entradas.put(voluntarioId, new Entrada(avisos, relogio.getAsLong() + ttlNanos));
        return new Resultado(avisos, "MISS");
    }
    public synchronized void invalidar(String voluntarioId) { entradas.remove(voluntarioId); }
}
