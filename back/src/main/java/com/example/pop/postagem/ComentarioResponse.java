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

    /** Comentário/resposta sem filhos aninhados. {@code pacienteAtual} = paciente logado (ou nulo). */
    public static ComentarioResponse from(Comentario c, Long pacienteAtual) {
        boolean dono = ehDono(c, pacienteAtual);
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
    public static ComentarioResponse from(Comentario c, List<Comentario> respostas, Long pacienteAtual) {
        List<ComentarioResponse> filhos = respostas.stream().map(r -> from(r, pacienteAtual)).toList();
        boolean dono = ehDono(c, pacienteAtual);
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

    /** true quando o comentário pertence ao paciente logado (base para editar/excluir). */
    private static boolean ehDono(Comentario c, Long pacienteAtual) {
        return pacienteAtual != null && pacienteAtual.equals(c.getPacienteId());
    }

    /**
     * true se ainda está dentro da janela de edição. Calculado no servidor (relógio
     * consistente): o app não faz conta de tempo/fuso — só mostra/oculta "Editar".
     */
    private static boolean dentroDaJanela(Comentario c) {
        return c.getCriadoEm() != null
                && c.getCriadoEm().isAfter(LocalDateTime.now().minusMinutes(JANELA_EDICAO_MINUTOS));
    }
}
