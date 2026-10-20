package com.example.pop.storage;

/**
 * Status do S3 de um escopo para a tela (SEM o segredo em claro — só {@code secretPreenchido}).
 * {@code configurado} = existe linha salva para o escopo.
 */
public record StatusS3(
        boolean configurado,
        String bucket,
        String region,
        String publicEndpoint,
        String internalEndpoint,
        String accessKey,
        boolean secretPreenchido,
        boolean ativo) {

    public static StatusS3 vazio() {
        return new StatusS3(false, "", "", "", "", "", false, true);
    }
}
