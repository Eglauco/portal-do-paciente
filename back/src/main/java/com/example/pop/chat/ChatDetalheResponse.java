package com.example.pop.chat;

import java.util.List;

import com.example.pop.common.Ref;

/** Detalhe do chat + mensagens (tela de conversa). */
public record ChatDetalheResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        StatusChat status,
        String statusDescricao,
        boolean pacienteUsandoApp,
        /** Atendente responsável pela conversa (nulo = ninguém assumiu). */
        Long responsavelId,
        String responsavelNome,
        List<MensagemResponse> mensagens) {
}
