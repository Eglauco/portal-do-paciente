package com.example.pop.storage;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code pasta}: subpasta (prefixo) no bucket. Opcional — quando ausente, cai em
 * "prontuarios" (retrocompatível). Ex.: "rede-social" para imagens de postagens.
 */
public record UploadUrlRequest(
        @NotBlank String nomeArquivo,
        String contentType,
        String pasta) {
}
