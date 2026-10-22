package com.example.pop.credencialia;

import java.time.Duration;
import java.util.List;

/**
 * Requisição provider-agnostic para a IA: um system prompt + a lista de turnos (conversa) + limites.
 * O {@link ConfigIaService#gerar} resolve o provedor/modelo do escopo e traduz isto para o LLM certo.
 */
public record RequisicaoIa(String system, List<Turno> turnos, long maxTokens, Duration timeout) {

    /** Requisição de um único turno de usuário (moderação/resumo). */
    public static RequisicaoIa unica(String system, String textoUsuario, long maxTokens, Duration timeout) {
        return new RequisicaoIa(system, List.of(Turno.usuario(textoUsuario)), maxTokens, timeout);
    }

    /** Requisição de um turno de usuário com anexo (análise de documento). */
    public static RequisicaoIa comAnexo(String system, String textoUsuario, Anexo anexo, long maxTokens, Duration timeout) {
        return new RequisicaoIa(system, List.of(Turno.usuario(textoUsuario, anexo)), maxTokens, timeout);
    }
}
