package com.example.pop.storage;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Monta clients/presigners S3 a partir de uma {@link DadosS3} resolvida. O presigner usa o endpoint
 * PÚBLICO (o navegador acessa as URLs pré-assinadas); o client usa o endpoint INTERNO (servidor-a-servidor).
 * Ambos path-style (MinIO/S3-compatível). Os objetos criados são caros e devem ser cacheados/fechados.
 */
final class S3Fabrica {

    private S3Fabrica() {
    }

    static S3Presigner presigner(DadosS3 d) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(d.publicEndpoint()))
                .region(Region.of(d.region()))
                .credentialsProvider(credenciais(d))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    static S3Client client(DadosS3 d) {
        return S3Client.builder()
                .endpointOverride(URI.create(d.internalEndpoint()))
                .region(Region.of(d.region()))
                .credentialsProvider(credenciais(d))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credenciais(DadosS3 d) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(d.accessKey(), d.secretKey()));
    }
}
