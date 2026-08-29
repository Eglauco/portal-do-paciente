package com.example.pop.chat;

import java.time.LocalDateTime;

public record MensagemResponse(
        Long id,
        RemetenteMensagem remetente,
        String texto,
        LocalDateTime enviadaEm,
        boolean lida,
        boolean entregue,
        String clienteId) {

    public static MensagemResponse from(Mensagem m) {
        return new MensagemResponse(m.getId(), m.getRemetente(), m.getTexto(), m.getEnviadaEm(),
                m.isLida(), m.isEntregue(), m.getClienteId());
    }
}
