package com.example.pop.storage;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.configuracao.SegredoCripto;
import com.example.pop.tenant.TenantContext;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Resolve a config de S3 por ESCOPO (unidade / padrão do inquilino / plataforma), decifrando a
 * secret-key, e administra o cadastro (CRUD write-only + testar conexão). A config da plataforma vem
 * do {@code application.properties} (global); unidade e padrão-inquilino vêm da tabela
 * {@code configuracao_s3_unidade} (no schema do inquilino). Cacheia a config resolvida por
 * {@code schema|cacheId} e invalida ao salvar (publicando {@link ConfigS3AlteradaEvento} para o
 * {@link StorageService} limpar os clients).
 */
@Service
public class ConfigS3Service {

    private final ConfiguracaoS3Repository repo;
    private final SegredoCripto cripto;
    private final ApplicationEventPublisher eventos;
    private final DadosS3 plataforma;

    /** Cache de config resolvida (com secret decifrada) por "schema|cacheId". */
    private final ConcurrentHashMap<String, DadosS3> cache = new ConcurrentHashMap<>();

    public ConfigS3Service(
            ConfiguracaoS3Repository repo,
            SegredoCripto cripto,
            ApplicationEventPublisher eventos,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.region}") String region,
            @Value("${storage.public-endpoint}") String publicEndpoint,
            @Value("${storage.internal-endpoint}") String internalEndpoint) {
        this.repo = repo;
        this.cripto = cripto;
        this.eventos = eventos;
        this.plataforma = new DadosS3(bucket, region,
                normalizarPublico(publicEndpoint), normalizar(internalEndpoint), accessKey, secretKey);
    }

    // ===================== Resolução (usada pelo StorageService) =====================

    /**
     * Config resolvida+decifrada do escopo; vazio se não configurado/inativo.
     * <p>UNIDADE: se a unidade tem config PRÓPRIA ativa, usa-a; se tem própria mas INATIVA, bloqueia
     * (vazio — não herda); se NÃO tem config própria, herda o S3 PADRÃO do inquilino. Nunca cai na
     * plataforma/global para dados de unidade/inquilino.
     */
    public Optional<DadosS3> resolver(EscopoS3 escopo) {
        if (escopo == null) {
            return Optional.empty();
        }
        return switch (escopo.tipo()) {
            case PLATAFORMA -> plataforma.completo() ? Optional.of(plataforma) : Optional.empty();
            case INQUILINO_PADRAO -> resolverPadrao();
            case UNIDADE -> resolverUnidade(escopo);
        };
    }

    private Optional<DadosS3> resolverUnidade(EscopoS3 escopo) {
        String chave = TenantContext.atual() + "|" + escopo.cacheId();
        DadosS3 cacheado = cache.get(chave);
        if (cacheado != null) {
            return Optional.of(cacheado);
        }
        Optional<ConfiguracaoS3> propria = repo.findByUnidadeId(escopo.unidadeId());
        Optional<DadosS3> resolvido;
        if (propria.isPresent()) {
            if (!propria.get().isAtivo()) {
                return Optional.empty(); // config própria inativa = bloqueada (não herda, não cacheia)
            }
            resolvido = Optional.of(dados(propria.get()));
        } else {
            resolvido = resolverPadrao(); // sem config própria → herda o S3 padrão do inquilino
        }
        resolvido.ifPresent(d -> cache.put(chave, d));
        return resolvido;
    }

    private Optional<DadosS3> resolverPadrao() {
        String chave = TenantContext.atual() + "|inq";
        DadosS3 cacheado = cache.get(chave);
        if (cacheado != null) {
            return Optional.of(cacheado);
        }
        Optional<DadosS3> carregado = repo.findByUnidadeIdIsNull().filter(ConfiguracaoS3::isAtivo).map(this::dados);
        carregado.ifPresent(d -> cache.put(chave, d));
        return carregado;
    }

    /** Config do escopo ou erro 422 claro (sem fallback global para dados de unidade/inquilino). */
    public DadosS3 exigir(EscopoS3 escopo) {
        return resolver(escopo).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagemNaoConfigurado(escopo)));
    }

    private static String mensagemNaoConfigurado(EscopoS3 escopo) {
        if (escopo == null) {
            return "Armazenamento (S3) não configurado.";
        }
        return switch (escopo.tipo()) {
            case UNIDADE -> "Armazenamento (S3) não configurado para esta unidade. "
                    + "Configure o S3 da unidade (Cadastros → Unidade → Armazenamento) ou o S3 padrão "
                    + "do inquilino (Configurações → Armazenamento) antes de enviar arquivos.";
            case INQUILINO_PADRAO -> "Armazenamento (S3) padrão da clínica não configurado. "
                    + "Configure em Configurações → Armazenamento antes de enviar arquivos.";
            case PLATAFORMA -> "Armazenamento (S3) da plataforma não configurado.";
        };
    }

    /** Monta {@link DadosS3} a partir da linha de config, decifrando a secret-key. */
    private DadosS3 dados(ConfiguracaoS3 c) {
        return new DadosS3(c.getBucket(), c.getRegion(), c.getPublicEndpoint(), c.getInternalEndpoint(),
                c.getAccessKey(), cripto.decifrar(c.getSecretKey()));
    }

    // ===================== Cadastro (tela) =====================

    public StatusS3 status(EscopoS3 escopo) {
        Optional<ConfiguracaoS3> e = escopo.tipo() == EscopoS3.Tipo.UNIDADE
                ? repo.findByUnidadeId(escopo.unidadeId())
                : repo.findByUnidadeIdIsNull();
        return e.map(c -> new StatusS3(true, c.getBucket(), c.getRegion(), c.getPublicEndpoint(),
                c.getInternalEndpoint(), c.getAccessKey(), StringUtils.hasText(c.getSecretKey()), c.isAtivo()))
                .orElseGet(StatusS3::vazio);
    }

    @Transactional
    public void salvar(EscopoS3 escopo, FormS3 form) {
        if (escopo.tipo() == EscopoS3.Tipo.PLATAFORMA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O S3 da plataforma é configurado por variáveis de ambiente, não por esta tela.");
        }
        Long unidadeId = escopo.tipo() == EscopoS3.Tipo.UNIDADE ? escopo.unidadeId() : null;
        Optional<ConfiguracaoS3> existente = unidadeId != null
                ? repo.findByUnidadeId(unidadeId)
                : repo.findByUnidadeIdIsNull();

        exigirTexto(form.bucket(), "Informe o bucket.");
        exigirTexto(form.accessKey(), "Informe o Access Key.");
        exigirTexto(form.publicEndpoint(), "Informe o endpoint público.");
        if (existente.isEmpty() && !StringUtils.hasText(form.secretKey())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o Secret Key.");
        }

        LocalDateTime agora = LocalDateTime.now();
        ConfiguracaoS3 c = existente.orElseGet(() -> {
            ConfiguracaoS3 nova = new ConfiguracaoS3();
            nova.setUnidadeId(unidadeId);
            nova.setCriadoEm(agora);
            return nova;
        });
        c.setBucket(form.bucket().trim());
        c.setRegion(StringUtils.hasText(form.region()) ? form.region().trim() : "us-east-1");
        c.setPublicEndpoint(normalizarPublico(form.publicEndpoint()));
        c.setInternalEndpoint(normalizar(StringUtils.hasText(form.internalEndpoint())
                ? form.internalEndpoint() : form.publicEndpoint()));
        c.setAccessKey(form.accessKey().trim());
        // Secret em branco MANTÉM o atual (write-only); preenchido → cifra o novo.
        if (StringUtils.hasText(form.secretKey())) {
            c.setSecretKey(cripto.cifrar(form.secretKey().trim()));
        }
        c.setAtivo(form.ativo());
        c.setAtualizadoEm(agora);
        repo.save(c);
        // Invalida APÓS o commit (StorageService também ouve) — evita re-cachear o valor antigo
        // numa leitura concorrente feita antes do commit.
        eventos.publishEvent(new ConfigS3AlteradaEvento(TenantContext.atual()));
    }

    /**
     * Remove a config PRÓPRIA do escopo (unidade ou padrão do inquilino). Para a unidade, isso a faz
     * voltar a HERDAR o S3 padrão do inquilino (sem config própria = herda). No-op se não havia config.
     */
    @Transactional
    public void remover(EscopoS3 escopo) {
        if (escopo == null || escopo.tipo() == EscopoS3.Tipo.PLATAFORMA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O S3 da plataforma é configurado por variáveis de ambiente, não por esta tela.");
        }
        Optional<ConfiguracaoS3> existente = escopo.tipo() == EscopoS3.Tipo.UNIDADE
                ? repo.findByUnidadeId(escopo.unidadeId())
                : repo.findByUnidadeIdIsNull();
        existente.ifPresent(repo::delete);
        eventos.publishEvent(new ConfigS3AlteradaEvento(TenantContext.atual()));
    }

    /** Limpa o cache de config resolvida do schema alterado, depois do commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarConfig(ConfigS3AlteradaEvento ev) {
        cache.keySet().removeIf(k -> k.startsWith(ev.schema() + "|"));
    }

    /** Testa a conexão (headBucket) com a config já SALVA do escopo. */
    public ResultadoTeste testar(EscopoS3 escopo) {
        DadosS3 cfg = resolver(escopo).orElse(null);
        if (cfg == null) {
            return new ResultadoTeste(false, "Armazenamento não configurado para este escopo.");
        }
        return testar(cfg);
    }

    /**
     * Testa a conexão com os valores do FORMULÁRIO (sem precisar salvar antes). A Secret Key em branco
     * usa a já salva (o usuário pode testar após mudar só o bucket/endpoint).
     */
    public ResultadoTeste testar(EscopoS3 escopo, FormS3 form) {
        if (form == null) {
            return testar(escopo);
        }
        DadosS3 cfg = dadosDoForm(escopo, form);
        if (cfg == null || !cfg.completo()) {
            return new ResultadoTeste(false, "Preencha endpoint, bucket, Access Key e Secret Key para testar.");
        }
        return testar(cfg);
    }

    private static ResultadoTeste testar(DadosS3 cfg) {
        try (S3Client client = S3Fabrica.client(cfg)) {
            client.headBucket(HeadBucketRequest.builder().bucket(cfg.bucket()).build());
            return new ResultadoTeste(true, "Conexão OK — bucket acessível.");
        } catch (RuntimeException e) {
            String causa = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ResultadoTeste(false, "Falha ao conectar: " + causa);
        }
    }

    /** Monta a config a partir do formulário; Secret em branco → usa a salva. Endpoints normalizados. */
    private DadosS3 dadosDoForm(EscopoS3 escopo, FormS3 form) {
        String secret = StringUtils.hasText(form.secretKey()) ? form.secretKey().trim() : secretSalvada(escopo);
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        String interno = normalizar(StringUtils.hasText(form.internalEndpoint())
                ? form.internalEndpoint() : form.publicEndpoint());
        return new DadosS3(
                form.bucket() == null ? "" : form.bucket().trim(),
                StringUtils.hasText(form.region()) ? form.region().trim() : "us-east-1",
                normalizarPublico(form.publicEndpoint()), interno,
                form.accessKey() == null ? "" : form.accessKey().trim(),
                secret);
    }

    private String secretSalvada(EscopoS3 escopo) {
        Optional<ConfiguracaoS3> e = escopo.tipo() == EscopoS3.Tipo.UNIDADE
                ? repo.findByUnidadeId(escopo.unidadeId())
                : repo.findByUnidadeIdIsNull();
        return e.map(c -> cripto.decifrar(c.getSecretKey())).orElse(null);
    }

    public record ResultadoTeste(boolean ok, String mensagem) {
    }

    // ===================== Internos =====================

    private static void exigirTexto(String v, String msg) {
        if (!StringUtils.hasText(v)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
        }
    }

    /** Remove barra final (e portas padrão 80/443 — casa com o Host que o navegador envia). */
    private static String normalizarPublico(String endpoint) {
        return normalizar(endpoint).replaceFirst(":443$", "").replaceFirst(":80$", "");
    }

    private static String normalizar(String endpoint) {
        String ep = endpoint == null ? "" : endpoint.trim();
        while (ep.endsWith("/")) {
            ep = ep.substring(0, ep.length() - 1);
        }
        return ep;
    }
}
