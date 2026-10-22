package com.example.pop.credencialia;

/**
 * Corpo do cadastro da credencial de IA. {@code apiKey} em branco MANTÉM a atual (write-only).
 * {@code provedor} é obrigatório (default ANTHROPIC se vier vazio); {@code baseUrl} é opcional
 * (Azure/compatível). Os 3 modelos são obrigatórios e precisam existir ativos — no PROVEDOR escolhido —
 * no cadastro "Modelos de IA".
 */
public record FormIa(
        String provedor,
        String apiKey,
        String baseUrl,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario,
        boolean ativo) {
}
