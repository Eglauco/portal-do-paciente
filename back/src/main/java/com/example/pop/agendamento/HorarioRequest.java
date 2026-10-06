package com.example.pop.agendamento;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

/**
 * Criação/edição de um Horário (marcação) numa Agenda. Na criação: paciente + hora de início (a data vem da
 * agenda). Na edição pela unidade, normalmente só o {@code statusAgendamento} muda.
 */
public record HorarioRequest(
        @NotNull Long agendaId,
        @NotNull Long pacienteId,
        @NotNull LocalTime horaInicio,
        LocalTime horaFim,
        StatusAgendamento statusAgendamento) {
}
