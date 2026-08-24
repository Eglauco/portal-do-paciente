package com.example.pop.agendamento;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgendamentoRequest(
        @NotNull LocalDateTime dataHora,
        @NotBlank String especialidade,
        @NotBlank String profissionalSaude,
        @NotNull Long pacienteId,
        @NotNull Long unidadeSaudeId,
        StatusAgendamento statusAgendamento) {
}
