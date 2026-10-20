package com.example.pop.storage;

import org.springframework.util.StringUtils;

/**
 * Config de S3 já RESOLVIDA e DECIFRADA para um escopo (pronta para montar o client/presigner).
 * Os endpoints já vêm normalizados. {@code secretKey} em claro — nunca sai por API.
 */
public record DadosS3(
        String bucket,
        String region,
        String publicEndpoint,
        String internalEndpoint,
        String accessKey,
        String secretKey) {

    /** true se tem o mínimo para operar (bucket + credenciais + endpoints). */
    public boolean completo() {
        return StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(publicEndpoint)
                && StringUtils.hasText(internalEndpoint)
                && StringUtils.hasText(region);
    }
}
