package com.example.pop.sau;

import java.util.List;

import com.example.pop.common.Ref;

/** Detalhe da manifestação + thread de mensagens. */
public record ManifestacaoDetalheResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        Ref tipo,
        StatusManifestacao status,
        String statusDescricao,
        List<MensagemSauResponse> mensagens) {
}
