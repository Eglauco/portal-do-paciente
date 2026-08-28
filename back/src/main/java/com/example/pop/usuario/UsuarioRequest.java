package com.example.pop.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um usuário. A senha é obrigatória na criação e
 * opcional na edição (em branco = mantém a senha atual).
 */
public record UsuarioRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        String senha) {
}
