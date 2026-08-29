package com.example.pop.chat;

import java.security.Principal;

/**
 * Identidade autenticada de uma conexão STOMP do chat: o papel (PACIENTE/ADMIN),
 * o id (id do paciente, ou id do usuário do back-office) e o aparelho da sessão
 * (claim {@code dev}, usado para revalidar a sessão do paciente por assinatura).
 */
public record ChatPrincipal(String nome, String role, Long id, String dev) implements Principal {

    @Override
    public String getName() {
        return nome;
    }
}
