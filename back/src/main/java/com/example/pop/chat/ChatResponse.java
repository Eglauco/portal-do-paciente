package com.example.pop.chat;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item da listagem de chats (estilo lista de conversas). */
public record ChatResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        StatusChat status,
        String statusDescricao,
        String ultimaMensagem,
        RemetenteMensagem ultimaMensagemDe,
        LocalDateTime ultimaMensagemEm,
        long naoLidas,
        LocalDateTime atualizadoEm) {
}
