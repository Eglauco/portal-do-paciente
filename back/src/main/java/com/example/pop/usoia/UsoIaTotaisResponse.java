package com.example.pop.usoia;

import java.math.BigDecimal;

/** Totais do filtro atual (para bater com a fatura da plataforma), com nulos tratados para zero. */
public record UsoIaTotaisResponse(BigDecimal custoUsd, long tokensEntrada, long tokensSaida, long registros) {

    public static UsoIaTotaisResponse from(UsoIaTotais t) {
        if (t == null) {
            return new UsoIaTotaisResponse(BigDecimal.ZERO, 0, 0, 0);
        }
        return new UsoIaTotaisResponse(
                t.custoUsd() == null ? BigDecimal.ZERO : t.custoUsd(),
                t.tokensEntrada() == null ? 0 : t.tokensEntrada(),
                t.tokensSaida() == null ? 0 : t.tokensSaida(),
                t.registros() == null ? 0 : t.registros());
    }
}
