package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de um comentário/resposta. O autor NÃO vem do corpo: é resolvido no servidor
 * pelos ids do comentário (paciente pelo token; "Administração" quando é do back-office).
 */
public record ComentarRequest(
        @NotBlank String texto) {
}
