package com.example.pop.storage;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code pasta}: subpasta (prefixo temático) no bucket. Opcional — ausente cai em "prontuarios".
 * Ex.: "rede-social" para imagens de postagens.
 * {@code unidadeId}: unidade DONA do arquivo (prontuário, postagem…) — define o S3 da unidade.
 * Ausente = S3 PADRÃO do inquilino (fotos de profissional, logomarca, modelos de TCLE).
 */
public record UploadUrlRequest(
        @NotBlank String nomeArquivo,
        String contentType,
        String pasta,
        Long unidadeId) {
}
