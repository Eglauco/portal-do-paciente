package com.example.pop.notificacaoadmin;

import java.time.LocalDateTime;

/** Item da lista de notificações do admin (sino do back-office). */
public record NotificacaoAdminResponse(
        Long id,
        TipoNotificacaoAdmin tipo,
        String titulo,
        String corpo,
        /** Rota do front para navegar ao clicar. */
        String rota,
        Long referenciaId,
        boolean lida,
        LocalDateTime criadoEm) {

    public static NotificacaoAdminResponse from(NotificacaoAdmin n) {
        return new NotificacaoAdminResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getCorpo(),
                n.getRota(), n.getReferenciaId(), n.isLida(), n.getCriadoEm());
    }
}
