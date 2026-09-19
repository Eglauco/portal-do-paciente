package com.example.pop.prontuario;

import jakarta.validation.constraints.NotBlank;

/** Payload de criação/edição do Tipo de Documento do Prontuário. */
public record TipoDocumentoProntuarioRequest(
        @NotBlank String nome,
        String promptResumo,
        String promptValidacao,
        Boolean ativo) {
}
