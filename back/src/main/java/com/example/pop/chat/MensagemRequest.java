package com.example.pop.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemRequest(
        @NotBlank @Size(max = 4000) String texto,
        @Size(max = 60) String clienteId) {
}
