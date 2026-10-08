package com.example.pop.agendamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Horário do paciente (app). Diferente da resposta do back-office, aqui a Agenda vem ANINHADA
 * ({@code agenda: { data, especialidade, profissionalSaude, configuracaoAgenda, unidadeSaude }}) — o "agendamento"
 * do paciente é um Horário que referencia uma Agenda. O {@code id} é o id do Horário (o que o paciente age).
 */
public record MeuHorarioResponse(
        Long id,
        LocalDateTime dataHora,
        LocalTime horaInicio,
        LocalTime horaFim,
        AgendaRef agenda,
        RefResponse paciente,
        StatusAgendamento statusAgendamento,
        String statusDescricao,
        boolean faltaJustificada,
        String justificativaFalta,
        List<RefResponse> motivosFalta,
        Integer horasCancelamento) {

    /** Dados da Agenda (slot) embutidos no horário do paciente. */
    public record AgendaRef(
            Long id,
            LocalDate data,
            RefResponse especialidade,
            RefResponse profissionalSaude,
            RefResponse configuracaoAgenda,
            RefResponse unidadeSaude) {
    }

    public static MeuHorarioResponse from(Horario h) {
        Agenda ag = h.getAgenda();
        return new MeuHorarioResponse(
                h.getId(),
                h.getDataHora(),
                h.getDataHora().toLocalTime(),
                h.getHoraFim(),
                new AgendaRef(
                        ag.getId(),
                        ag.getData(),
                        new RefResponse(ag.getEspecialidade().getId(), ag.getEspecialidade().getNome()),
                        new RefResponse(ag.getProfissionalSaude().getId(), ag.getProfissionalSaude().getNome()),
                        new RefResponse(ag.getConfiguracaoAgenda().getId(), ag.getConfiguracaoAgenda().getNome()),
                        new RefResponse(ag.getUnidadeSaude().getId(), ag.getUnidadeSaude().getNome())),
                new RefResponse(h.getPaciente().getId(), h.getPaciente().getNome()),
                h.getStatusAgendamento(),
                h.getStatusAgendamento().getDescricao(),
                h.getFaltaJustificadaEm() != null,
                h.getJustificativaFalta(),
                h.getMotivosFalta().stream().map(m -> new RefResponse(m.getId(), m.getMotivo())).toList(),
                h.getConfiguracaoAgenda().getHorasCancelamento());
    }
}
