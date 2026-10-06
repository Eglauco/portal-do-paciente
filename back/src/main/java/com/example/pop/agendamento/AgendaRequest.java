package com.example.pop.agendamento;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Criação/edição de uma Agenda (slot): dia + profissional/especialidade/procedimento/unidade. */
public record AgendaRequest(
        @NotNull LocalDate data,
        @NotNull Long profissionalSaudeId,
        @NotNull Long especialidadeId,
        @NotNull Long procedimentoId,
        @NotNull Long unidadeSaudeId) {
}
