package com.example.pop.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import com.example.pop.tenant.TenantContext;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Integração com o S3/MinIO, agora POR ESCOPO ({@link EscopoS3}: unidade / padrão do inquilino /
 * plataforma). Cada objeto é gravado com um PREFIXO de escopo na key ({@code u<id>/}, {@code inq/},
 * {@code plat/}), e a leitura/exclusão descobre o escopo só pela URL salva — sem o chamador precisar
 * repassar a unidade. A config (endpoint/bucket/credenciais) de cada escopo vem do {@link ConfigS3Service};
 * clients/presigners são cacheados por {@code schema|prefixo} e invalidados quando a config muda.
 *
 * <p>O upload é feito direto do navegador para o S3 via URL pré-assinada (o arquivo nunca passa pelo backend).
 */
@Service
public class StorageService {

    private final ConfigS3Service configS3;

    private final Map<String, S3Presigner> presigners = new ConcurrentHashMap<>();
    private final Map<String, S3Client> clients = new ConcurrentHashMap<>();

    public StorageService(ConfigS3Service configS3) {
        this.configS3 = configS3;
    }

    // ===================== Escrita (precisa do escopo) =====================

    /** Gera uma URL pré-assinada (PUT) para o navegador enviar o arquivo direto ao S3 do escopo. */
    public UploadUrlResponse gerarUploadUrl(EscopoS3 escopo, String nomeArquivo, String contentType, String pasta) {
        DadosS3 cfg = configS3.exigir(escopo);
        String tipo = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        String chave = chaveNova(escopo, pasta, nomeArquivo);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.bucket()).key(chave).contentType(tipo).build();
        PresignedPutObjectRequest presigned = presigner(escopo, cfg).presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(put).build());

        return new UploadUrlResponse(presigned.url().toString(), urlPublica(cfg, chave));
    }

    /**
     * Grava bytes gerados no backend direto no S3 do escopo (putObject server-side) e devolve a URL pública.
     * Usado quando o arquivo NÃO vem do navegador (ex.: PDF assinado baixado da ZapSign). Escopo sem S3
     * configurado sobe como erro 422.
     */
    public String salvarBytes(EscopoS3 escopo, byte[] conteudo, String contentType, String pasta, String nomeArquivo) {
        DadosS3 cfg = configS3.exigir(escopo);
        String tipo = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        String chave = chaveNova(escopo, pasta, nomeArquivo);
        client(escopo, cfg).putObject(
                PutObjectRequest.builder().bucket(cfg.bucket()).key(chave).contentType(tipo).build(),
                RequestBody.fromBytes(conteudo));
        return urlPublica(cfg, chave);
    }

    // ===================== Leitura / exclusão (escopo vem da URL) =====================

    /** Gera uma URL pré-assinada (GET) temporária para VISUALIZAR o arquivo (inline). */
    public String gerarDownloadUrl(String url) {
        return gerarDownloadUrl(url, null);
    }

    /**
     * Gera uma URL pré-assinada (GET) temporária. Com {@code nomeArquivo}, a resposta vem com
     * {@code Content-Disposition: attachment} (o navegador BAIXA com esse nome). Objetos legados (sem
     * prefixo de escopo) são devolvidos como estão (não assináveis).
     */
    public String gerarDownloadUrl(String url, String nomeArquivo) {
        EscopoS3 escopo = escopoDaUrl(url);
        if (escopo == null) {
            return url;
        }
        DadosS3 cfg = configS3.exigir(escopo);
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return url;
        }
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(cfg.bucket()).key(chave);
        if (StringUtils.hasText(nomeArquivo)) {
            get.responseContentDisposition("attachment; filename=\"" + nomeAnexoSeguro(nomeArquivo) + "\"");
        }
        PresignedGetObjectRequest presigned = presigner(escopo, cfg).presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(get.build()).build());
        return presigned.url().toString();
    }

    /**
     * URL de visualização (GET pré-assinada) com validade configurável. Fail-soft: devolve a URL
     * recebida se o escopo não estiver configurado / URL inválida / erro (não quebra a tela).
     */
    public String urlVisualizacao(String url, Duration duracao) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        DadosS3 cfg = configS3.resolver(escopoDaUrl(url)).orElse(null);
        String chave = chaveDaUrl(url);
        if (cfg == null || chave == null) {
            return url;
        }
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(cfg.bucket()).key(chave).build();
            PresignedGetObjectRequest presigned = presigner(escopoDaUrl(url), cfg).presignGetObject(
                    GetObjectPresignRequest.builder().signatureDuration(duracao).getObjectRequest(get).build());
            return presigned.url().toString();
        } catch (RuntimeException e) {
            return url;
        }
    }

    /** URL de visualização (GET pré-assinada) da foto do paciente, válida por 6h; a própria URL se não houver S3. */
    public String urlFotoPaciente(String fotoUrl) {
        return urlVisualizacao(fotoUrl, Duration.ofHours(6));
    }

    /**
     * true se a URL aponta para um objeto dentro da subpasta informada (2º segmento da key, após o
     * prefixo de escopo). Defesa para o app só salvar como foto uma URL realmente da pasta esperada.
     */
    public boolean urlNaPasta(String url, String pasta) {
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return false;
        }
        int i = chave.indexOf('/');
        if (i < 0) {
            return false;
        }
        String aposPrefixo = chave.substring(i + 1); // pasta/UUID-arquivo
        return aposPrefixo.startsWith(pastaSegura(pasta) + "/");
    }

    /**
     * Baixa os bytes do objeto (server-side, endpoint interno). Fail-soft: null se escopo não
     * configurado / URL inválida / objeto ausente.
     */
    public byte[] baixarBytes(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        EscopoS3 escopo = escopoDaUrl(url);
        DadosS3 cfg = configS3.resolver(escopo).orElse(null);
        String chave = chaveDaUrl(url);
        if (cfg == null || chave == null) {
            return null;
        }
        try {
            return client(escopo, cfg).getObjectAsBytes(
                    GetObjectRequest.builder().bucket(cfg.bucket()).key(chave).build()).asByteArray();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Remove o objeto no S3 a partir da URL salva. Fail-soft se escopo não configurado / URL inválida. */
    public void excluirPorUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        EscopoS3 escopo = escopoDaUrl(url);
        DadosS3 cfg = configS3.resolver(escopo).orElse(null);
        String chave = chaveDaUrl(url);
        if (cfg == null || chave == null) {
            return;
        }
        client(escopo, cfg).deleteObject(DeleteObjectRequest.builder().bucket(cfg.bucket()).key(chave).build());
    }

    // ===================== Cache de clients/presigners =====================

    private S3Presigner presigner(EscopoS3 escopo, DadosS3 cfg) {
        return presigners.computeIfAbsent(TenantContext.atual() + "|" + escopo.prefixo(), k -> S3Fabrica.presigner(cfg));
    }

    private S3Client client(EscopoS3 escopo, DadosS3 cfg) {
        return clients.computeIfAbsent(TenantContext.atual() + "|" + escopo.prefixo(), k -> S3Fabrica.client(cfg));
    }

    /** Config de S3 mudou: descarta os clients/presigners cacheados daquele schema (recriados on-demand). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarConfig(ConfigS3AlteradaEvento ev) {
        String prefixo = ev.schema() + "|";
        fecharERemover(presigners, prefixo, S3Presigner::close);
        fecharERemover(clients, prefixo, S3Client::close);
    }

    private static <T> void fecharERemover(Map<String, T> mapa, String prefixoChave, Consumer<T> fechar) {
        mapa.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(prefixoChave)) {
                try {
                    fechar.accept(e.getValue());
                } catch (RuntimeException ignore) {
                    // best-effort ao fechar o client antigo
                }
                return true;
            }
            return false;
        });
    }

    // ===================== Helpers de key/URL =====================

    /** Nova key com prefixo de escopo: {@code <prefixo>/<pasta>/<UUID>-<nome>}. */
    private String chaveNova(EscopoS3 escopo, String pasta, String nomeArquivo) {
        return escopo.prefixo() + "/" + pastaSegura(pasta) + "/" + UUID.randomUUID() + "-" + sanitizar(nomeArquivo);
    }

    private static String urlPublica(DadosS3 cfg, String chave) {
        return cfg.publicEndpoint() + "/" + cfg.bucket() + "/" + chave;
    }

    /** Escopo do objeto a partir do 1º segmento da key (prefixo); null se legado/irreconhecível. */
    private EscopoS3 escopoDaUrl(String url) {
        String chave = chaveDaUrl(url);
        if (chave == null) {
            return null;
        }
        int i = chave.indexOf('/');
        return EscopoS3.doPrefixo(i < 0 ? chave : chave.substring(0, i));
    }

    /** Key do objeto = tudo após o 1º segmento do path (o bucket). Endpoint/bucket-agnóstico. */
    private String chaveDaUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int i = path.indexOf('/'); // fim do segmento do bucket
            if (i < 0) {
                return null;
            }
            String chave = path.substring(i + 1);
            return chave.isEmpty() ? null : chave;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Remove aspas/quebras/controle do nome (evita injeção no header Content-Disposition). */
    private static String nomeAnexoSeguro(String nome) {
        return nome.replaceAll("[\"\\\\\\r\\n]", "").trim();
    }

    private static String sanitizar(String nome) {
        if (!StringUtils.hasText(nome)) {
            return "arquivo";
        }
        String limpo = nome.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return limpo.length() > 120 ? limpo.substring(limpo.length() - 120) : limpo;
    }

    /** Subpasta (prefixo temático) segura: só minúsculas/dígitos/hífen. Vazio → "prontuarios". */
    private static String pastaSegura(String pasta) {
        if (!StringUtils.hasText(pasta)) {
            return "prontuarios";
        }
        String limpa = pasta.trim().toLowerCase().replaceAll("[^a-z0-9-]", "");
        return limpa.isEmpty() ? "prontuarios" : limpa;
    }
}
