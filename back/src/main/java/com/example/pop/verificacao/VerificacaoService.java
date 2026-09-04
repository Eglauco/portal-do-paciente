package com.example.pop.verificacao;

/**
 * Verificação de posse do telefone (OTP) do paciente. Abstrai o provedor (Twilio
 * Verify) para ser mockável nos testes e trocável no futuro.
 */
public interface VerificacaoService {

    /** Envia um código de verificação para o telefone (E.164) pelo canal escolhido. */
    void enviar(String telefoneE164, CanalVerificacao canal);

    /** Confere o código informado para o telefone (E.164); true se aprovado. */
    boolean checar(String telefoneE164, String codigo);
}
