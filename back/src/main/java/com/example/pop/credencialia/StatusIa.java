package com.example.pop.credencialia;

/**
 * Status da credencial de IA de um escopo para a tela. A api-key nunca volta em claro — só o flag
 * {@code apiKeyPreenchida}. {@code provedor} e {@code baseUrl} dizem qual LLM o escopo usa. Os modelos
 * são obrigatórios e escolhidos no cadastro "Modelos de IA" (filtrados pelo provedor).
 */
public record StatusIa(
        boolean configurado,
        boolean apiKeyPreenchida,
        String provedor,
        String baseUrl,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        boolean ativo) {
}
