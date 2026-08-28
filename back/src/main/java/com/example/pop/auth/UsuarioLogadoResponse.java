package com.example.pop.auth;

/** Dados do usuário autenticado (GET /auth/me). */
public record UsuarioLogadoResponse(String nome, String email) {
}
