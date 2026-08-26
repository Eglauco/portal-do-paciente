package com.example.pop.prontuario;

import jakarta.validation.constraints.NotBlank;

public record DocumentoRequest(@NotBlank String nome, String url) {
}
