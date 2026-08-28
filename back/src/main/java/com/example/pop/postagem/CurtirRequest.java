package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;

public record CurtirRequest(@NotBlank String dispositivoId) {
}
