package com.example.pop.credencialia;

/**
 * Corpo do cadastro da credencial de IA. {@code apiKey} em branco MANTÉM a atual (write-only).
 * Os 3 modelos são obrigatórios e precisam existir ativos no cadastro "Modelos de IA".
 */
public record FormIa(
        String apiKey,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        boolean ativo) {
}
