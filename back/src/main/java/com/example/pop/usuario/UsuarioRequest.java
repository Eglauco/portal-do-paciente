package com.example.pop.usuario;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um usuário. A senha é obrigatória na criação e
 * opcional na edição (em branco = mantém a senha atual). A unidade é obrigatória
 * e o usuário precisa de ao menos um perfil de acesso (a permissão é a união deles).
 */
public record UsuarioRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        String senha,
        @NotNull Long unidadeSaudeId,
        @NotEmpty(message = "Selecione ao menos um perfil de acesso") List<Long> perfilIds) {
}
