package com.example.pop.storage;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Integração com o S3/MinIO. O upload do arquivo é feito direto do navegador
 * para o S3 usando uma URL pré-assinada gerada aqui — o arquivo nunca passa
 * pelo backend (evita sobrecarga de banda). O backend só guarda a URL e é
 * responsável por remover o objeto quando o documento é excluído.
 */
@Service
public class StorageService {

    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String publicEndpoint;
    private final String internalEndpoint;

    private S3Presigner presigner;
    private S3Client client;

    public StorageService(
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.region}") String region,
            @Value("${storage.public-endpoint}") String publicEndpoint,
            @Value("${storage.internal-endpoint}") String internalEndpoint) {
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.publicEndpoint = normalizar(publicEndpoint);
        this.internalEndpoint = normalizarPreservandoPorta(internalEndpoint);
    }

    private boolean configurado() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }

    private void exigirConfiguracao() {
        if (!configurado()) {
            throw new IllegalStateException("Armazenamento (S3/MinIO) não configurado.");
        }
    }

    private StaticCredentialsProvider credenciais() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    // Clientes criados sob demanda (o SDK rejeita credenciais vazias na criação).
    private synchronized S3Presigner presigner() {
        if (presigner == null) {
            presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(publicEndpoint))
                    .region(Region.of(region))
                    .credentialsProvider(credenciais())
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        }
        return presigner;
    }

    private synchronized S3Client client() {
        if (client == null) {
            client = S3Client.builder()
                    .endpointOverride(URI.create(internalEndpoint))
                    .region(Region.of(region))
                    .credentialsProvider(credenciais())
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        }
        return client;
    }

    /** Gera uma URL pré-assinada (PUT) para o navegador enviar o arquivo direto ao S3. */
    public UploadUrlResponse gerarUploadUrl(String nomeArquivo, String contentType, String pasta) {
        exigirConfiguracao();
        String tipo = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        String chave = pastaSegura(pasta) + "/" + UUID.randomUUID() + "-" + sanitizar(nomeArquivo);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(chave)
                .contentType(tipo)
                .build();

        PresignedPutObjectRequest presigned = presigner().presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(put)
                        .build());

        String uploadUrl = presigned.url().toString();
        String publicUrl = publicEndpoint + "/" + bucket + "/" + chave;
        return new UploadUrlResponse(uploadUrl, publicUrl);
    }

    /** Gera uma URL pré-assinada (GET) temporária para visualizar/baixar o arquivo. */
    public String gerarDownloadUrl(String url) {
        exigirConfiguracao();
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return url;
        }
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(chave).build();
        PresignedGetObjectRequest presigned = presigner().presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(get)
                        .build());
        return presigned.url().toString();
    }

    /**
     * URL de visualização (GET pré-assinada) com validade configurável.
     * Não lança erro se o armazenamento não estiver configurado: devolve a URL
     * recebida (útil em ambientes sem S3, como testes).
     */
    public String urlVisualizacao(String url, Duration duracao) {
        if (!StringUtils.hasText(url) || !configurado()) {
            return url;
        }
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return url;
        }
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(chave).build();
            PresignedGetObjectRequest presigned = presigner().presignGetObject(
                    GetObjectPresignRequest.builder().signatureDuration(duracao).getObjectRequest(get).build());
            return presigned.url().toString();
        } catch (RuntimeException e) {
            return url;
        }
    }

    /** Remove o objeto no S3 a partir da URL salva no documento. */
    public void excluirPorUrl(String url) {
        if (!StringUtils.hasText(url) || !configurado()) {
            return;
        }
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return;
        }
        client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
    }

    /** Extrai a chave do objeto (após "/bucket/") a partir da URL. */
    private String chaveDaUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            String prefixo = bucket + "/";
            if (path.startsWith(prefixo)) {
                return path.substring(prefixo.length());
            }
            return path.isEmpty() ? null : path;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String sanitizar(String nome) {
        if (!StringUtils.hasText(nome)) {
            return "arquivo";
        }
        String limpo = nome.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return limpo.length() > 120 ? limpo.substring(limpo.length() - 120) : limpo;
    }

    /**
     * Subpasta (prefixo) segura: só minúsculas/dígitos/hífen (evita "../" e chaves
     * estranhas). Vazio/ausente cai em "prontuarios" (retrocompatível).
     */
    private String pastaSegura(String pasta) {
        if (!StringUtils.hasText(pasta)) {
            return "prontuarios";
        }
        String limpa = pasta.trim().toLowerCase().replaceAll("[^a-z0-9-]", "");
        return limpa.isEmpty() ? "prontuarios" : limpa;
    }

    /** Remove barra final e portas padrão (para casar com o Host que o navegador envia). */
    private String normalizar(String endpoint) {
        String ep = normalizarPreservandoPorta(endpoint);
        return ep.replaceFirst(":443$", "").replaceFirst(":80$", "");
    }

    private String normalizarPreservandoPorta(String endpoint) {
        String ep = endpoint == null ? "" : endpoint.trim();
        while (ep.endsWith("/")) {
            ep = ep.substring(0, ep.length() - 1);
        }
        return ep;
    }
}
