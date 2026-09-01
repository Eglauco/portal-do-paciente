package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;

/** Edição do texto de um comentário do paciente. */
public record EditarComentarioRequest(@NotBlank String texto) {
}
