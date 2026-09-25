package com.example.pop.procedimento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro/edição de um documento TCLE do procedimento (admin). O arquivo é enviado direto ao S3
 * (URL pré-assinada) e aqui chega só a {@code url} pública + o nome e o content-type.
 */
public record TermoProcedimentoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank String url,
        @Size(max = 120) String contentType) {
}
