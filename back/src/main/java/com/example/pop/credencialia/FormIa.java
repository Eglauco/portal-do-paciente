package com.example.pop.credencialia;

/**
 * Corpo do cadastro da credencial de IA. {@code apiKey} em branco MANTÉM a atual (write-only).
 * Modelos em branco = usa o padrão da plataforma.
 */
public record FormIa(
        String apiKey,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        boolean ativo) {
}
