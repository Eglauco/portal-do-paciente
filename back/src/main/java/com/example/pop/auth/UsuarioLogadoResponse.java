package com.example.pop.auth;

import com.example.pop.usuario.Usuario;

/** Dados do usuário autenticado (GET /auth/me, PUT /auth/unidade). */
public record UsuarioLogadoResponse(
        String nome,
        String email,
        Long unidadeSaudeId,
        String unidadeSaudeNome) {

    public static UsuarioLogadoResponse from(Usuario u) {
        return new UsuarioLogadoResponse(
                u.getNome(),
                u.getEmail(),
                u.getUnidade() == null ? null : u.getUnidade().getId(),
                u.getUnidade() == null ? null : u.getUnidade().getNome());
    }
}
