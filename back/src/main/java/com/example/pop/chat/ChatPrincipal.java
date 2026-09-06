package com.example.pop.chat;

import java.security.Principal;

/**
 * Identidade autenticada de uma conexão STOMP do chat: o papel (PACIENTE/ADMIN),
 * o id (id do paciente do perfil ativo, ou id do usuário do back-office), a conta
 * do app ({@code cid}) e o aparelho da sessão ({@code dev}) — usados para revalidar
 * a sessão do paciente a cada assinatura. A conta é nula para o admin e para tokens
 * antigos (sem cid); nesse caso a revalidação cai no modelo legado (perfil próprio).
 */
public record ChatPrincipal(String nome, String role, Long id, Long cid, String dev) implements Principal {

    @Override
    public String getName() {
        return nome;
    }
}
