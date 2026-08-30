package com.example.pop.chat;

import jakarta.validation.constraints.NotNull;

/** Pedido do back-office para abrir (ou reutilizar) a conversa de um paciente na unidade. */
public record AbrirConversaRequest(
        @NotNull Long pacienteId,
        @NotNull Long unidadeId) {
}
