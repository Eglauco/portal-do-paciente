package com.example.pop.chat;

import java.security.Principal;

/**
 * Identidade autenticada de uma conexão STOMP do chat: o papel (PACIENTE/ADMIN)
 * e o id (id do paciente, ou id do usuário do back-office).
 */
public record ChatPrincipal(String nome, String role, Long id) implements Principal {

    @Override
    public String getName() {
        return nome;
    }
}
