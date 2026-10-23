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
        /** Quando foi entregue ao aparelho (2º check); nulo se ainda não entregue. */
        LocalDateTime entregueEm,
        /** Quando o paciente visualizou (recibo "lido"); nulo se ainda não visualizada. */
        LocalDateTime visualizadaEm,
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
        BigDecimal custoUsd,
        /** Mensagem citada (feature "responder", estilo WhatsApp); nula quando não é uma resposta. */
        Citacao respondeA) {

    /**
     * Trecho da mensagem citada — o suficiente pro cliente desenhar o bloco de citação
     * (barra + autor + prévia) sem precisar buscar a mensagem original na lista.
     */
    public record Citacao(Long id, RemetenteMensagem remetente, String texto, boolean geradaPorIa,
            String atendenteNome) {
    }

    private static final int MAX_TRECHO = 160;

    public static MensagemResponse from(Mensagem m) {
        return from(m, null);
    }

    public static MensagemResponse from(Mensagem m, String responsavelNome) {
        return new MensagemResponse(m.getId(), m.getRemetente(), m.getTexto(), m.getEnviadaEm(),
                m.isLida(), m.isEntregue(), m.getEntregueEm(), m.getVisualizadaEm(), m.getClienteId(),
                m.getUsuario() != null ? m.getUsuario().getNome() : null,
                responsavelNome,
                m.isGeradaPorIa(),
                m.getTokensEntrada(),
                m.getTokensSaida(),
                m.getModeloIa(),
                m.getCustoUsd(),
                citacaoDe(m.getRespondeA()));
    }

    /** Monta o trecho citado (null-safe). A mensagem citada vem com o atendente já carregado. */
    private static Citacao citacaoDe(Mensagem r) {
        if (r == null) {
            return null;
        }
        return new Citacao(r.getId(), r.getRemetente(), trecho(r.getTexto()), r.isGeradaPorIa(),
                r.getUsuario() != null ? r.getUsuario().getNome() : null);
    }

    private static String trecho(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip();
        return t.length() <= MAX_TRECHO ? t : t.substring(0, MAX_TRECHO).stripTrailing() + "…";
    }
}
