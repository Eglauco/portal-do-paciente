package com.example.pop.chat;

import jakarta.validation.constraints.NotBlank;

public record MensagemRequest(@NotBlank String texto) {
}
