package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.common.Ref;

/** Detalhe do chat + mensagens (tela de conversa). */
public record ChatDetalheResponse(
        Long id,
        Ref paciente,
        /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
        String pacienteFotoUrl,
        Ref unidadeSaude,
        StatusChat status,
        String statusDescricao,
        boolean pacienteUsandoApp,
        /** Atendente responsável pela conversa (nulo = ninguém assumiu). */
        Long responsavelId,
        String responsavelNome,
        /** Até quando o paciente leu — o atendente mostra "lido" nas mensagens até esta data. */
        LocalDateTime pacienteLeuEm,
        List<MensagemResponse> mensagens) {
}
