package com.example.pop.postagem;

import java.time.LocalDateTime;
import java.util.List;

public record ComentarioResponse(
        Long id,
        String autor,
        String texto,
        LocalDateTime criadoEm,
        boolean editado,
        boolean meu,
        boolean podeEditar,
        List<ComentarioResponse> respostas) {

    /** Janela em que o autor ainda pode editar o próprio comentário (fonte única). */
    public static final int JANELA_EDICAO_MINUTOS = 15;

    /**
     * Comentário/resposta sem filhos aninhados. O "dono" é conferido contra o
     * paciente logado ({@code pacienteAtual}) OU o admin logado ({@code adminAtual});
     * qualquer um pode ser nulo conforme quem está lendo (app = paciente, front = admin).
     */
    public static ComentarioResponse from(Comentario c, Long pacienteAtual, Long adminAtual) {
        boolean dono = ehDono(c, pacienteAtual, adminAtual);
        return new ComentarioResponse(
                c.getId(),
                c.getAutor(),
                c.getTexto(),
                c.getCriadoEm(),
                c.getEditadoEm() != null,
                dono,
                dono && dentroDaJanela(c),
                List.of());
    }

    /** Comentário-raiz com suas respostas (as respostas não aninham mais níveis). */
    public static ComentarioResponse from(Comentario c, List<Comentario> respostas, Long pacienteAtual, Long adminAtual) {
        List<ComentarioResponse> filhos = respostas.stream().map(r -> from(r, pacienteAtual, adminAtual)).toList();
        boolean dono = ehDono(c, pacienteAtual, adminAtual);
        return new ComentarioResponse(
                c.getId(),
                c.getAutor(),
                c.getTexto(),
                c.getCriadoEm(),
                c.getEditadoEm() != null,
                dono,
                dono && dentroDaJanela(c),
                filhos);
    }

    /** true quando o comentário pertence a quem está lendo (paciente OU admin). */
    private static boolean ehDono(Comentario c, Long pacienteAtual, Long adminAtual) {
        return (pacienteAtual != null && pacienteAtual.equals(c.getPacienteId()))
                || (adminAtual != null && adminAtual.equals(c.getUsuarioId()));
    }

    /**
     * true se ainda está dentro da janela de edição. Calculado no servidor (relógio
     * consistente): os clientes não fazem conta de tempo/fuso — só mostram/ocultam "Editar".
     */
    private static boolean dentroDaJanela(Comentario c) {
        return c.getCriadoEm() != null
                && c.getCriadoEm().isAfter(LocalDateTime.now().minusMinutes(JANELA_EDICAO_MINUTOS));
    }
}
