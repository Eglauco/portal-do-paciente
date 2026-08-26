package com.example.pop.push;

import jakarta.validation.constraints.NotBlank;

public record RegistrarDispositivoRequest(@NotBlank String token) {
}
