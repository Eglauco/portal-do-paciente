package com.example.pop.postagem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public record ComentarioResponse(
        Long id,
        String autor,
        String fotoUrl,
        /** Nome (abreviado) do responsável que comentou pelo paciente, ou null se foi o próprio. */
        String responsavelNome,
        String texto,
        LocalDateTime criadoEm,
        boolean editado,
        boolean meu,
        boolean podeEditar,
        /** Estado de moderação (PUBLICADO/PENDENTE/REJEITADO). PENDENTE = "em análise". */
        StatusModeracao statusModeracao,
        /** Motivo da IA quando pendente — só preenchido para o admin. */
        String motivoModeracao,
        List<ComentarioResponse> respostas) {

    /** Janela em que o autor ainda pode editar o próprio comentário (fonte única). */
    public static final int JANELA_EDICAO_MINUTOS = 15;

    /**
     * Comentário/resposta sem filhos aninhados. {@code fotoDoPaciente} resolve a foto
     * (URL pré-assinada) pelo {@code pacienteId} do autor — null quando não é do paciente
     * ou não tem foto. {@code nomeDoResponsavel} resolve o nome (abreviado) pelo
     * {@code responsavelId} — null quando foi o próprio paciente. O "dono" é conferido
     * contra o paciente logado ({@code pacienteAtual}) OU o admin logado ({@code adminAtual}).
     */
    public static ComentarioResponse from(Comentario c, Long pacienteAtual, Long adminAtual,
            Function<Long, String> fotoDoPaciente, Function<Long, String> nomeDoResponsavel) {
        boolean dono = ehDono(c, pacienteAtual, adminAtual);
        return new ComentarioResponse(
                c.getId(),
                c.getAutor(),
                fotoDoPaciente.apply(c.getPacienteId()),
                nomeDoResponsavel.apply(c.getResponsavelId()),
                c.getTexto(),
                c.getCriadoEm(),
                c.getEditadoEm() != null,
                dono,
                dono && dentroDaJanela(c),
                c.getStatusModeracao(),
                adminAtual != null ? c.getMotivoModeracao() : null,
                List.of());
    }

    /** Comentário-raiz com suas respostas (as respostas não aninham mais níveis). */
    public static ComentarioResponse from(Comentario c, List<Comentario> respostas, Long pacienteAtual,
            Long adminAtual, Function<Long, String> fotoDoPaciente, Function<Long, String> nomeDoResponsavel) {
        List<ComentarioResponse> filhos = respostas.stream()
                .map(r -> from(r, pacienteAtual, adminAtual, fotoDoPaciente, nomeDoResponsavel)).toList();
        boolean dono = ehDono(c, pacienteAtual, adminAtual);
        return new ComentarioResponse(
                c.getId(),
                c.getAutor(),
                fotoDoPaciente.apply(c.getPacienteId()),
                nomeDoResponsavel.apply(c.getResponsavelId()),
                c.getTexto(),
                c.getCriadoEm(),
                c.getEditadoEm() != null,
                dono,
                dono && dentroDaJanela(c),
                c.getStatusModeracao(),
                adminAtual != null ? c.getMotivoModeracao() : null,
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
