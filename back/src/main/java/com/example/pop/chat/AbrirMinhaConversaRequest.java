package com.example.pop.chat;

import jakarta.validation.constraints.NotNull;

/** Abertura de conversa pelo paciente (a unidade; o paciente vem do token). */
public record AbrirMinhaConversaRequest(@NotNull Long unidadeId) {
}
