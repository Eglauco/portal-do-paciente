package com.example.pop.chat;

import java.time.LocalDateTime;

public record MensagemResponse(
        Long id,
        RemetenteMensagem remetente,
        String texto,
        LocalDateTime enviadaEm,
        boolean lida,
        boolean entregue,
        String clienteId,
        /** Nome do atendente que enviou (só nas mensagens da unidade); nulo caso contrário. */
        String atendenteNome,
        /** Nome do responsável que enviou em nome do paciente (perfil dependente); nulo se foi o próprio. */
        String responsavelNome) {

    public static MensagemResponse from(Mensagem m) {
        return from(m, null);
    }

    public static MensagemResponse from(Mensagem m, String responsavelNome) {
        return new MensagemResponse(m.getId(), m.getRemetente(), m.getTexto(), m.getEnviadaEm(),
                m.isLida(), m.isEntregue(), m.getClienteId(),
                m.getUsuario() != null ? m.getUsuario().getNome() : null,
                responsavelNome);
    }
}
