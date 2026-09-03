package com.example.pop.notificacao;

import java.time.LocalDateTime;

/** Item da lista de notificações do paciente. */
public record NotificacaoResponse(
        Long id,
        TipoNotificacao tipo,
        String titulo,
        String corpo,
        Long referenciaId,
        boolean lida,
        LocalDateTime criadoEm) {

    public static NotificacaoResponse from(Notificacao n) {
        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getCorpo(),
                n.getReferenciaId(), n.isLida(), n.getCriadoEm());
    }
}
