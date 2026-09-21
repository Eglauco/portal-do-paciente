package com.example.pop.chat;

import java.math.BigDecimal;
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
        String responsavelNome,
        /** Mensagem gerada pela assistente virtual (IA), não por um atendente humano. */
        boolean geradaPorIa,
        /** Tokens gastos pela IA neste turno (só em mensagens da IA); exibidos apenas no back-office. */
        Long tokensEntrada,
        Long tokensSaida,
        /** Modelo de IA usado (só em mensagens da IA); exibido apenas no back-office. */
        String modeloIa,
        /** Custo (US$) do turno da IA; exibido apenas no back-office. */
        BigDecimal custoUsd) {

    public static MensagemResponse from(Mensagem m) {
        return from(m, null);
    }

    public static MensagemResponse from(Mensagem m, String responsavelNome) {
        return new MensagemResponse(m.getId(), m.getRemetente(), m.getTexto(), m.getEnviadaEm(),
                m.isLida(), m.isEntregue(), m.getClienteId(),
                m.getUsuario() != null ? m.getUsuario().getNome() : null,
                responsavelNome,
                m.isGeradaPorIa(),
                m.getTokensEntrada(),
                m.getTokensSaida(),
                m.getModeloIa(),
                m.getCustoUsd());
    }
}
