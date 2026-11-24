package br.org.igreja.comum;

import com.google.gson.Gson;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class Contrato {
    public static final Gson JSON = new Gson();
    public static final String FILA = "escalas.publicadas.v1";
    public record Voluntario(String id, String nome) {}
    public record NovaEscala(String evento, String data, List<Voluntario> voluntarios) {}
    public record Escala(String id, String evento, String data, List<Voluntario> voluntarios,
                         String status, String eventoId) {}
    public record EscalaPublicada(int versao, String tipo, String eventoId, String escalaId,
                                 String ocorridoEm, String evento, String data, List<Voluntario> voluntarios) {}
    public record Aviso(String id, String eventoId, String escalaId, String voluntarioId,
                       String voluntarioNome, String mensagem, String criadoEm) {}

    public static String texto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > max)
            throw new IllegalArgumentException(campo + " deve ter entre 1 e " + max + " caracteres");
        return valor;
    }

    public static void validar(NovaEscala escala) {
        if (escala == null) throw new IllegalArgumentException("Informe a escala");
        texto(escala.evento(), 120, "evento");
        LocalDate.parse(texto(escala.data(), 10, "data"));
        if (escala.voluntarios() == null || escala.voluntarios().isEmpty() || escala.voluntarios().size() > 100)
            throw new IllegalArgumentException("Informe de 1 a 100 voluntários");
        var ids = new HashSet<String>();
        for (var v : escala.voluntarios()) {
            if (v == null) throw new IllegalArgumentException("Voluntário inválido");
            texto(v.id(), 64, "voluntario.id");
            texto(v.nome(), 120, "voluntario.nome");
            if (!ids.add(v.id())) throw new IllegalArgumentException("Voluntário repetido");
        }
    }

    public static void validar(EscalaPublicada evento) {
        if (evento == null || evento.versao() != 1 || !"EscalaPublicada".equals(evento.tipo()))
            throw new IllegalArgumentException("Tipo ou versão de evento não suportado");
        UUID.fromString(texto(evento.eventoId(), 36, "eventoId"));
        UUID.fromString(texto(evento.escalaId(), 36, "escalaId"));
        Instant.parse(texto(evento.ocorridoEm(), 40, "ocorridoEm"));
        validar(new NovaEscala(evento.evento(), evento.data(), evento.voluntarios()));
    }

    private Contrato() {}
}
