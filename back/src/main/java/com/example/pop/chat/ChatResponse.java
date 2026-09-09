package com.example.pop.chat;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item da listagem de chats (estilo lista de conversas). */
public record ChatResponse(
        Long id,
        Ref paciente,
        /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
        String pacienteFotoUrl,
        Ref unidadeSaude,
        StatusChat status,
        String statusDescricao,
        String ultimaMensagem,
        RemetenteMensagem ultimaMensagemDe,
        LocalDateTime ultimaMensagemEm,
        long naoLidas,
        /** Há mensagem da unidade que o PACIENTE ainda não leu (indicador no app do paciente). */
        boolean naoLidaPaciente,
        LocalDateTime atualizadoEm,
        /** Atendente responsável pela conversa (nulo = ninguém assumiu). */
        Long responsavelId,
        String responsavelNome) {
}
