package com.example.pop.credencialia;

/**
 * Credencial de IA resolvida (já decifrada) de um escopo. A {@code apiKey} está presente quando o
 * escopo tem credencial ativa; os modelos podem vir em branco (= usar o padrão da plataforma).
 */
public record CredenciaisIa(String apiKey, String modeloModeracao, String modeloChat, String modeloProntuario) {
}
