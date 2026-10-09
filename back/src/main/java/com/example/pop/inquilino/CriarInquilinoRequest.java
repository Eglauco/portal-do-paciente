package com.example.pop.inquilino;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cadastro de um inquilino (super-admin): o inquilino + o schema a provisionar + a 1ª unidade e o
 * 1º admin (para o cliente já conseguir entrar). O schema é validado como identificador Postgres
 * seguro (minúsculo, começa por letra/underscore, ≤ 63 caracteres) — barra injeção no CREATE SCHEMA.
 */
public record CriarInquilinoRequest(
        @NotBlank String nome,
        @NotBlank
        @Pattern(regexp = "[a-z_][a-z0-9_]{0,62}",
                message = "nome do schema inválido: minúsculo, começa por letra ou _, até 63 caracteres")
        String schemaName,
        @NotBlank String unidadeNome,
        @NotBlank String adminNome,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminSenha) {
}
