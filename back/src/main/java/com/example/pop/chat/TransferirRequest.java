package com.example.pop.chat;

import jakarta.validation.constraints.NotNull;

/** Transferência da conversa para outro atendente (o usuário que passa a ser o responsável). */
public record TransferirRequest(@NotNull Long usuarioId) {
}
