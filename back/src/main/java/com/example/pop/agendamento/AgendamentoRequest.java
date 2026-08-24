package com.example.pop.agendamento;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record AgendamentoRequest(
        @NotNull LocalDateTime dataHora,
        @NotNull Long especialidadeId,
        @NotNull Long profissionalSaudeId,
        @NotNull Long procedimentoId,
        @NotNull Long pacienteId,
        @NotNull Long unidadeSaudeId,
        StatusAgendamento statusAgendamento) {
}
