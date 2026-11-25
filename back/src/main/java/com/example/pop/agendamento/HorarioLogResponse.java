package com.example.pop.agendamento;

import java.time.LocalDateTime;

/**
 * Item da linha do tempo do agendamento: uma troca de status OU um evento (ex.: convite do app
 * enviado por SMS), e quem fez. Só um dos nomes de ator vem preenchido, conforme {@code autor}.
 * Nas linhas de evento, {@code evento} vem preenchido e os campos de status vêm nulos.
 */
public record HorarioLogResponse(
        Long id,
        AutorLogHorario autor,
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
        LocalDateTime criadoEm,
        /** Evento que não é troca de status (nulo nas linhas de status). */
        EventoLogHorario evento,
        String eventoDescricao,
        /** Complemento do evento (ex.: número mascarado do convite). */
        String detalhe) {

    public static HorarioLogResponse from(HorarioLog l) {
        return new HorarioLogResponse(
                l.getId(),
                l.getAutor(),
                l.getPaciente() != null ? l.getPaciente().nomeExibicao() : null,
                l.getResponsavel() != null ? l.getResponsavel().getNome() : null,
                l.getUsuario() != null ? l.getUsuario().getNome() : null,
                l.getStatusAnterior(),
                l.getStatusAnterior() != null ? l.getStatusAnterior().getDescricao() : null,
                l.getStatusNovo(),
                l.getStatusNovo() != null ? l.getStatusNovo().getDescricao() : null,
                l.getCriadoEm(),
                l.getEvento(),
                l.getEvento() != null ? l.getEvento().getDescricao() : null,
                l.getDetalhe());
    }
}
