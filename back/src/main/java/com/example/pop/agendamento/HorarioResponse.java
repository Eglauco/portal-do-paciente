package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Horário (marcação de um paciente numa Agenda) — usado na lista de horários da agenda e no detalhe. */
public record HorarioResponse(
        Long id,
        Long agendaId,
        LocalDateTime dataHora,
        LocalTime horaInicio,
        LocalTime horaFim,
        RefResponse paciente,
        String pacienteCpf,
        StatusAgendamento statusAgendamento,
        String statusDescricao,
        boolean faltaJustificada,
        String justificativaFalta,
        List<RefResponse> motivosFalta,
        Integer horasCancelamento,
        EstadoEntrega entregaResumo,
        String entregaResumoDescricao) {

    public static HorarioResponse from(Horario h) {
        EstadoEntrega entrega = h.getEntregaResumo();
        return new HorarioResponse(
                h.getId(),
                h.getAgenda().getId(),
                h.getDataHora(),
                h.getDataHora().toLocalTime(),
                h.getHoraFim(),
                new RefResponse(h.getPaciente().getId(), h.getPaciente().getNome()),
                h.getPaciente().getCpf(),
                h.getStatusAgendamento(),
                h.getStatusAgendamento().getDescricao(),
                h.getFaltaJustificadaEm() != null,
                h.getJustificativaFalta(),
                h.getMotivosFalta().stream().map(m -> new RefResponse(m.getId(), m.getMotivo())).toList(),
                h.getProcedimento().getHorasCancelamento(),
                entrega,
                entrega != null ? entrega.getDescricao() : null);
    }
}
