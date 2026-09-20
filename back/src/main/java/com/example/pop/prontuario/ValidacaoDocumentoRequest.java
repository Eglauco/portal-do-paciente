package com.example.pop.prontuario;

import jakarta.validation.constraints.NotNull;

/**
 * Decisão humana sobre o alerta de um documento. {@code decisao} deve ser
 * {@link StatusAnaliseDocumento#ALTERACAO_CONFIRMADA} (confirma a alteração) ou
 * {@link StatusAnaliseDocumento#SEM_ALTERACOES} (marca sem alteração); {@code observacao} é opcional.
 */
public record ValidacaoDocumentoRequest(@NotNull StatusAnaliseDocumento decisao, String observacao) {
}
