package com.example.pop.auth;

import java.time.Instant;
import java.util.List;

import com.example.pop.common.Ref;

/** Resposta do login: token JWT + dados básicos do usuário (unidade ativa + permissões efetivas). */
public record LoginResponse(
        String token,
        String nome,
        String email,
        Long unidadeSaudeId,
        String unidadeSaudeNome,
        Instant expiraEm,
        List<String> telas,
        List<Ref> unidades) {
}
