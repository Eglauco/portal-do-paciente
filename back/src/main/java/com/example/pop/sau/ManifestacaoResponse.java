package com.example.pop.sau;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item da listagem de manifestações (app do paciente e CRUD do SAU). */
public record ManifestacaoResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        Ref tipo,
        StatusManifestacao status,
        String statusDescricao,
        String ultimaMensagem,
        AutorManifestacao ultimaMensagemDe,
        LocalDateTime atualizadoEm,
        LocalDateTime criadoEm) {
}
