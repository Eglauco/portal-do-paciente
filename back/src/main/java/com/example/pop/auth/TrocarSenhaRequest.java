package com.example.pop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca da própria senha (admin logado): a atual (para conferir) e a nova. */
public record TrocarSenhaRequest(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 6, max = 100) String novaSenha) {
}
