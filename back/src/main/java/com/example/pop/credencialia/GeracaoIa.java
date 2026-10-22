package com.example.pop.credencialia;

/**
 * Resultado de uma geração de IA (provider-agnostic): o texto gerado + tokens gastos (entrada/saída) +
 * o id do modelo usado. As frentes usam os tokens + modelo para calcular e registrar o custo (uso_ia).
 */
public record GeracaoIa(String texto, long tokensEntrada, long tokensSaida, String modelo) {
}
