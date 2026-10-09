package com.example.pop.inquilino;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cadastro de um inquilino (super-admin): nome + o nome do schema Postgres que será criado e
 * provisionado. O schema é validado como identificador seguro (minúsculo, começa por letra/
 * underscore, ≤ 63 caracteres) — barra injeção no {@code CREATE SCHEMA}.
 */
public record CriarInquilinoRequest(
        @NotBlank String nome,
        @NotBlank
        @Pattern(regexp = "[a-z_][a-z0-9_]{0,62}",
                message = "nome do schema inválido: minúsculo, começa por letra ou _, até 63 caracteres")
        String schemaName) {
}
