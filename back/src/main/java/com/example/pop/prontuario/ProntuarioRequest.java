package com.example.pop.prontuario;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProntuarioRequest(
        @NotNull Long agendamentoId,
        @NotBlank String numeroAtendimento,
        @Valid List<DocumentoRequest> documentos) {

    public List<DocumentoRequest> documentos() {
        return documentos == null ? List.of() : documentos;
    }
}
