package com.example.pop.sau;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.common.Ref;

/** Detalhe da manifestação + thread de mensagens. */
public record ManifestacaoDetalheResponse(
        Long id,
        Ref paciente,
        /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
        String pacienteFotoUrl,
        Ref unidadeSaude,
        Ref tipo,
        StatusManifestacao status,
        String statusDescricao,
        /** Avaliação do atendimento (nulos até o paciente encerrar e avaliar). */
        Integer avaliacaoNota,
        String avaliacaoComentario,
        LocalDateTime avaliadoEm,
        List<MensagemSauResponse> mensagens) {
}
