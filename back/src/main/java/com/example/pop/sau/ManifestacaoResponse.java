package com.example.pop.sau;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item da listagem de manifestações (app do paciente e CRUD do SAU). */
public record ManifestacaoResponse(
        Long id,
        Ref paciente,
        /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
        String pacienteFotoUrl,
        Ref unidadeSaude,
        Ref tipo,
        StatusManifestacao status,
        String statusDescricao,
        String ultimaMensagem,
        AutorManifestacao ultimaMensagemDe,
        /** Nota do atendimento (1-5) quando o paciente já avaliou; senão nulo. */
        Integer avaliacaoNota,
        LocalDateTime atualizadoEm,
        LocalDateTime criadoEm) {
}
