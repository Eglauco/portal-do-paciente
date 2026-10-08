package com.example.pop.sms;

/**
 * Envio de SMS de texto livre. Abstrai o provedor (Twilio Messaging API) para ser
 * mockável nos testes e trocável no futuro. É <b>diferente</b> do {@code VerificacaoService}
 * (Twilio Verify), que só envia e valida OTP — este manda mensagem de texto arbitrária.
 */
public interface SmsService {

    /**
     * Envia um SMS de texto livre para o telefone (E.164). Fail-open: nunca lança —
     * devolve {@code true} se o provedor aceitou e {@code false} em qualquer falha
     * (sem credencial/remetente, número inválido, erro de rede/HTTP).
     */
    boolean enviar(String telefoneE164, String texto);

    /** {@code true} se há credenciais e um remetente configurados; sem isso nada é enviado. */
    boolean configurado();
}
