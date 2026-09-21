package com.example.pop.usoia;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha do ledger de uso de IA para o back-office. */
public record UsoIaResponse(
        Long id,
        UsoIaTipo tipo,
        String tipoDescricao,
        String descricao,
        String modeloIa,
        Long tokensEntrada,
        Long tokensSaida,
        BigDecimal custoUsd,
        String rota,
        LocalDateTime criadoEm) {

    public static UsoIaResponse from(UsoIa u) {
        return new UsoIaResponse(u.getId(), u.getTipo(),
                u.getTipo() == null ? null : u.getTipo().getDescricao(),
                u.getDescricao(), u.getModeloIa(), u.getTokensEntrada(), u.getTokensSaida(),
                u.getCustoUsd(), u.getRota(), u.getCriadoEm());
    }
}
