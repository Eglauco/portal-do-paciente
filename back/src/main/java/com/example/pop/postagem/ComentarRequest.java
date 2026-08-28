package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarRequest(
        @NotBlank @Size(max = 80) String autor,
        @NotBlank String texto) {
}
