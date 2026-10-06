package com.example.pop.agendamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Horário (marcação de um paciente numa Agenda) — na lista de horários da agenda e no detalhe do horário.
 * Traz também o contexto da Agenda (data + especialidade/profissional/procedimento/unidade) para a tela do horário
 * ser auto-suficiente.
 */
public record HorarioResponse(
        Long id,
        Long agendaId,
        LocalDate data,
        LocalDateTime dataHora,
        LocalTime horaInicio,
        LocalTime horaFim,
        RefResponse paciente,
        String pacienteCpf,
        RefResponse especialidade,
        RefResponse profissionalSaude,
        RefResponse procedimento,
        RefResponse unidadeSaude,
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
        Agenda ag = h.getAgenda();
        return new HorarioResponse(
                h.getId(),
                ag.getId(),
                ag.getData(),
                h.getDataHora(),
                h.getDataHora().toLocalTime(),
                h.getHoraFim(),
                new RefResponse(h.getPaciente().getId(), h.getPaciente().getNome()),
                h.getPaciente().getCpf(),
                new RefResponse(ag.getEspecialidade().getId(), ag.getEspecialidade().getNome()),
                new RefResponse(ag.getProfissionalSaude().getId(), ag.getProfissionalSaude().getNome()),
                new RefResponse(ag.getProcedimento().getId(), ag.getProcedimento().getNome()),
                new RefResponse(ag.getUnidadeSaude().getId(), ag.getUnidadeSaude().getNome()),
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
