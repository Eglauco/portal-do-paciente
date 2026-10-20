package com.example.pop.storage;

/**
 * Entrada do cadastro de S3 (por unidade ou padrão do inquilino). A {@code secretKey} em branco
 * MANTÉM a atual (write-only — não precisa redigitar o segredo para mudar outra coisa). O
 * {@code internalEndpoint} em branco cai no {@code publicEndpoint}.
 */
public record FormS3(
        String bucket,
        String region,
        String publicEndpoint,
        String internalEndpoint,
        String accessKey,
        String secretKey,
        boolean ativo) {
}
