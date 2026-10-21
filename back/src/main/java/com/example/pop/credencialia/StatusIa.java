package com.example.pop.credencialia;

/**
 * Status da credencial de IA de um escopo para a tela. A api-key nunca volta em claro — só o flag
 * {@code apiKeyPreenchida}. Os modelos são obrigatórios e escolhidos no cadastro "Modelos de IA",
 * então a tela não precisa mais de padrões-dica.
 */
public record StatusIa(
        boolean configurado,
        boolean apiKeyPreenchida,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        boolean ativo) {
}
