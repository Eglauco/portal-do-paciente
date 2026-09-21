package com.example.pop.usoia;

import java.math.BigDecimal;

/**
 * Soma do consumo de IA para o filtro atual (projeção da query de agregação). Campos de soma podem
 * vir null quando não há registros — o response trata para 0.
 */
public record UsoIaTotais(BigDecimal custoUsd, Long tokensEntrada, Long tokensSaida, Long registros) {
}
