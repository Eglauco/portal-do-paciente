package com.example.pop.chat;

import java.security.Principal;

/**
 * Identidade autenticada de uma conexão STOMP do chat: o papel (PACIENTE/ADMIN),
 * o id (id do paciente do perfil ativo, ou id do usuário do back-office), a conta
 * do app ({@code cid}), o aparelho da sessão ({@code dev}) e o SCHEMA do inquilino
 * ({@code schema}, resolvido do claim {@code inq} no CONNECT) — este último porque a
 * thread do WebSocket não passa pelo TenantFilter: as consultas de domínio de cada
 * frame (revalidar sessão, posse da conversa, permissão) precisam ser feitas no schema
 * do inquilino desta conexão. A conta é nula para o admin e para tokens antigos (sem cid);
 * nesse caso a revalidação cai no modelo legado (perfil próprio).
 */
public record ChatPrincipal(String nome, String role, Long id, Long cid, String dev, String schema)
        implements Principal {

    @Override
    public String getName() {
        return nome;
    }
}
