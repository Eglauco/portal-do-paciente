package com.example.pop.chat;

import java.time.LocalDateTime;

/** Item da linha do tempo de auditoria da conversa. */
public record ChatLogResponse(
        Long id,
        TipoLogChat tipo,
        String usuarioNome,
        String destinoNome,
        StatusChat statusAnterior,
        StatusChat statusNovo,
        LocalDateTime criadoEm) {

    public static ChatLogResponse from(ChatLog l) {
        return new ChatLogResponse(
                l.getId(),
                l.getTipo(),
                l.getUsuario() != null ? l.getUsuario().getNome() : null,
                l.getDestino() != null ? l.getDestino().getNome() : null,
                l.getStatusAnterior(),
                l.getStatusNovo(),
                l.getCriadoEm());
    }
}
