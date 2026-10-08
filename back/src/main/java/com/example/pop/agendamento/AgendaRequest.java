package com.example.pop.agendamento;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Criação/edição de uma Agenda (slot): dia + profissional/especialidade/configuracaoAgenda/unidade + nome (rótulo opcional).
 * O código de integração (CROSS) NÃO entra aqui — é chave de rastreio gravada só pela importação do SIRESP.
 */
public record AgendaRequest(
        @NotNull LocalDate data,
        @NotNull Long profissionalSaudeId,
        @NotNull Long especialidadeId,
        @NotNull Long configuracaoAgendaId,
        @NotNull Long unidadeSaudeId,
        String nome) {
}
