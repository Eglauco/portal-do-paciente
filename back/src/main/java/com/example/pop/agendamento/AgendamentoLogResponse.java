package com.example.pop.agendamento;

import java.time.LocalDateTime;

/**
 * Item da linha do tempo de status do agendamento: a transição e quem a fez.
 * Só um dos nomes de ator vem preenchido, conforme {@code autor}.
 */
public record AgendamentoLogResponse(
        Long id,
        AutorLogAgendamento autor,
        /** Nome do paciente (quando autor = PACIENTE). */
        String pacienteNome,
        /** Nome do responsável (quando autor = RESPONSAVEL). */
        String responsavelNome,
        /** Nome do atendente (quando autor = UNIDADE). */
        String usuarioNome,
        StatusAgendamento statusAnterior,
        String statusAnteriorDescricao,
        StatusAgendamento statusNovo,
        String statusNovoDescricao,
        LocalDateTime criadoEm) {

    public static AgendamentoLogResponse from(AgendamentoLog l) {
        return new AgendamentoLogResponse(
                l.getId(),
                l.getAutor(),
                l.getPaciente() != null ? l.getPaciente().getNome() : null,
                l.getResponsavel() != null ? l.getResponsavel().getNome() : null,
                l.getUsuario() != null ? l.getUsuario().getNome() : null,
                l.getStatusAnterior(),
                l.getStatusAnterior() != null ? l.getStatusAnterior().getDescricao() : null,
                l.getStatusNovo(),
                l.getStatusNovo() != null ? l.getStatusNovo().getDescricao() : null,
                l.getCriadoEm());
    }
}
