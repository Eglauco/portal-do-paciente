package com.example.pop.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemRequest(
        @NotBlank @Size(max = 4000) String texto,
        @Size(max = 60) String clienteId,
        /** Id da mensagem citada (feature "responder"); nulo quando não é uma resposta. */
        Long respondeAId) {

    /** Conveniência (sem citação) — mantém compatibilidade com chamadas que não respondem a nada. */
    public MensagemRequest(String texto, String clienteId) {
        this(texto, clienteId, null);
    }
}
