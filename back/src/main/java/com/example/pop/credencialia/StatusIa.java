package com.example.pop.credencialia;

/**
 * Status da credencial de IA de um escopo para a tela. A api-key nunca volta em claro — só o flag
 * {@code apiKeyPreenchida}. Inclui os modelos padrão da plataforma para o front mostrar como dica
 * ("em branco → usa &lt;padrão&gt;").
 */
public record StatusIa(
        boolean configurado,
        boolean apiKeyPreenchida,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        String modeloModeracaoPadrao,
        String modeloChatPadrao,
        String modeloProntuarioPadrao,
        boolean ativo) {
}
