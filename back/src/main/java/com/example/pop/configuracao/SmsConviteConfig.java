package com.example.pop.configuracao;

/**
 * Convite por SMS (liga/desliga + texto da mensagem) editado inline na mesma tela. O texto é a
 * mensagem INTEIRA do SMS; é obrigatório quando {@code habilitado} é true (validado ao salvar).
 */
public record SmsConviteConfig(boolean habilitado, String texto) {
}
