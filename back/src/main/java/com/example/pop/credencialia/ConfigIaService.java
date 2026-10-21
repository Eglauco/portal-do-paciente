package com.example.pop.credencialia;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.models.ModelInfo;
import com.anthropic.models.models.ModelListParams;
import com.example.pop.configuracao.SegredoCripto;
import com.example.pop.modeloia.ModeloIa;
import com.example.pop.modeloia.ModeloIaRepository;
import com.example.pop.tenant.TenantContext;

/**
 * Resolve a credencial de IA (Claude/Anthropic) por ESCOPO (unidade / padrão do inquilino), decifrando
 * a api-key, e administra o cadastro (CRUD write-only + testar a chave). É "nunca global": sem
 * credencial do cliente, {@link #cliente} vem vazio e cada frente de IA degrada como já faz hoje.
 *
 * <p>UNIDADE sem config própria HERDA o padrão do inquilino; config própria INATIVA bloqueia (não herda).
 * Cacheia as credenciais resolvidas por {@code schema|escopo} e os clients por {@code schema|escopo|timeout},
 * invalidados ao salvar/remover (via {@link ConfigIaAlteradaEvento} no commit).
 */
@Service
public class ConfigIaService {

    private final ConfiguracaoIaRepository repo;
    private final ModeloIaRepository modeloIaRepository;
    private final SegredoCripto cripto;
    private final ApplicationEventPublisher eventos;

    /**
     * Modelos padrão da plataforma (env) — fallback DEFENSIVO em {@link #modelo} caso uma credencial
     * antiga tenha chegado com o modelo em branco. No cadastro o modelo é obrigatório (ver {@link #salvar}).
     */
    private final String modeloModeracaoPadrao;
    private final String modeloChatPadrao;
    private final String modeloProntuarioPadrao;

    private final ConcurrentHashMap<String, CredenciaisIa> cacheCred = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnthropicClient> cacheClient = new ConcurrentHashMap<>();

    public ConfigIaService(
            ConfiguracaoIaRepository repo,
            ModeloIaRepository modeloIaRepository,
            SegredoCripto cripto,
            ApplicationEventPublisher eventos,
            @Value("${anthropic.moderacao.modelo:claude-haiku-4-5}") String modeloModeracaoPadrao,
            @Value("${anthropic.chat.modelo:claude-haiku-4-5}") String modeloChatPadrao,
            @Value("${anthropic.prontuario.modelo:claude-opus-5}") String modeloProntuarioPadrao) {
        this.repo = repo;
        this.modeloIaRepository = modeloIaRepository;
        this.cripto = cripto;
        this.eventos = eventos;
        this.modeloModeracaoPadrao = modeloModeracaoPadrao;
        this.modeloChatPadrao = modeloChatPadrao;
        this.modeloProntuarioPadrao = modeloProntuarioPadrao;
    }

    // ===================== Resolução (usada pelas frentes de IA) =====================

    /** Credencial resolvida+decifrada do escopo; vazio se não configurada/inativa (unidade herda inquilino). */
    public Optional<CredenciaisIa> resolver(EscopoIa escopo) {
        if (escopo == null) {
            return Optional.empty();
        }
        return switch (escopo.tipo()) {
            case INQUILINO_PADRAO -> resolverPadrao();
            case UNIDADE -> resolverUnidade(escopo);
        };
    }

    private Optional<CredenciaisIa> resolverUnidade(EscopoIa escopo) {
        String chave = TenantContext.atual() + "|" + escopo.cacheId();
        CredenciaisIa cacheado = cacheCred.get(chave);
        if (cacheado != null) {
            return Optional.of(cacheado);
        }
        Optional<ConfiguracaoIa> propria = repo.findByUnidadeId(escopo.unidadeId());
        Optional<CredenciaisIa> resolvido;
        if (propria.isPresent()) {
            if (!propria.get().isAtivo()) {
                return Optional.empty(); // config própria inativa = bloqueada (não herda, não cacheia)
            }
            resolvido = Optional.of(toCred(propria.get()));
        } else {
            resolvido = resolverPadrao(); // sem config própria → herda o padrão do inquilino
        }
        resolvido.ifPresent(c -> cacheCred.put(chave, c));
        return resolvido;
    }

    private Optional<CredenciaisIa> resolverPadrao() {
        String chave = TenantContext.atual() + "|inq";
        CredenciaisIa cacheado = cacheCred.get(chave);
        if (cacheado != null) {
            return Optional.of(cacheado);
        }
        Optional<CredenciaisIa> carregado = repo.findByUnidadeIdIsNull().filter(ConfiguracaoIa::isAtivo).map(this::toCred);
        carregado.ifPresent(c -> cacheCred.put(chave, c));
        return carregado;
    }

    private CredenciaisIa toCred(ConfiguracaoIa c) {
        return new CredenciaisIa(cripto.decifrar(c.getApiKey()),
                c.getModeloModeracao(), c.getModeloChat(), c.getModeloProntuario());
    }

    /** Client Anthropic do escopo (cacheado por schema|escopo|timeout); vazio se sem credencial/inativa. */
    public Optional<AnthropicClient> cliente(EscopoIa escopo, Duration timeout) {
        Optional<CredenciaisIa> cred = resolver(escopo);
        if (cred.isEmpty() || !StringUtils.hasText(cred.get().apiKey())) {
            return Optional.empty();
        }
        String chave = TenantContext.atual() + "|" + escopo.cacheId() + "|" + timeout.toSeconds();
        AnthropicClient c = cacheClient.computeIfAbsent(chave, k -> AnthropicOkHttpClient.builder()
                .apiKey(cred.get().apiKey())
                .timeout(timeout)
                .maxRetries(0)
                .build());
        return Optional.of(c);
    }

    /** Modelo da frente: o configurado pelo cliente para aquele escopo, ou o padrão da plataforma se em branco. */
    public String modelo(EscopoIa escopo, FrenteIa frente) {
        CredenciaisIa cred = resolver(escopo).orElse(null);
        String doCliente = cred == null ? null : switch (frente) {
            case MODERACAO -> cred.modeloModeracao();
            case CHAT -> cred.modeloChat();
            case PRONTUARIO -> cred.modeloProntuario();
        };
        if (StringUtils.hasText(doCliente)) {
            return doCliente.trim();
        }
        return switch (frente) {
            case MODERACAO -> modeloModeracaoPadrao;
            case CHAT -> modeloChatPadrao;
            case PRONTUARIO -> modeloProntuarioPadrao;
        };
    }

    // ===================== Cadastro (tela) =====================

    public StatusIa status(EscopoIa escopo) {
        return carregar(escopo)
                .map(c -> new StatusIa(true, StringUtils.hasText(c.getApiKey()),
                        c.getModeloModeracao(), c.getModeloChat(), c.getModeloProntuario(), c.isAtivo()))
                .orElseGet(() -> new StatusIa(false, false, "", "", "", true));
    }

    @Transactional
    public void salvar(EscopoIa escopo, FormIa form) {
        Long unidadeId = escopo.tipo() == EscopoIa.Tipo.UNIDADE ? escopo.unidadeId() : null;
        Optional<ConfiguracaoIa> existente = carregar(escopo);
        if (existente.isEmpty() && !StringUtils.hasText(form.apiKey())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a API key da Anthropic.");
        }
        LocalDateTime agora = LocalDateTime.now();
        ConfiguracaoIa c = existente.orElseGet(() -> {
            ConfiguracaoIa nova = new ConfiguracaoIa();
            nova.setUnidadeId(unidadeId);
            nova.setCriadoEm(agora);
            return nova;
        });
        // API key em branco MANTÉM a atual (write-only); preenchida → cifra a nova.
        if (StringUtils.hasText(form.apiKey())) {
            c.setApiKey(cripto.cifrar(form.apiKey().trim()));
        }
        // Modelos obrigatórios + validados contra o cadastro "Modelos de IA" (ativo) → o custo sempre computa.
        c.setModeloModeracao(exigirModeloAtivo(form.modeloModeracao(), "da moderação"));
        c.setModeloChat(exigirModeloAtivo(form.modeloChat(), "do chat"));
        c.setModeloProntuario(exigirModeloAtivo(form.modeloProntuario(), "do prontuário"));
        c.setAtivo(form.ativo());
        c.setAtualizadoEm(agora);
        repo.save(c);
        eventos.publishEvent(new ConfigIaAlteradaEvento(TenantContext.atual()));
    }

    /** Remove a credencial própria do escopo. Na unidade, volta a HERDAR o padrão do inquilino. */
    @Transactional
    public void remover(EscopoIa escopo) {
        carregar(escopo).ifPresent(repo::delete);
        eventos.publishEvent(new ConfigIaAlteradaEvento(TenantContext.atual()));
    }

    /** Invalida os caches do schema alterado (credenciais + clients), depois do commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterar(ConfigIaAlteradaEvento ev) {
        String pref = ev.schema() + "|";
        cacheCred.keySet().removeIf(k -> k.startsWith(pref));
        cacheClient.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(pref)) {
                fechar(e.getValue());
                return true;
            }
            return false;
        });
    }

    // ===================== Testar a chave =====================

    /** Testa a api-key do formulário (em branco usa a salva) validando com um list de modelos (sem gastar tokens). */
    public ResultadoTeste testar(EscopoIa escopo, FormIa form) {
        String apiKey = StringUtils.hasText(form == null ? null : form.apiKey())
                ? form.apiKey().trim()
                : apiKeySalvada(escopo);
        if (!StringUtils.hasText(apiKey)) {
            return new ResultadoTeste(false, "Informe a API key para testar.");
        }
        AnthropicClient c = AnthropicOkHttpClient.builder()
                .apiKey(apiKey).timeout(Duration.ofSeconds(20)).maxRetries(0).build();
        try {
            Set<String> ids = c.models().list(ModelListParams.builder().build()).data().stream()
                    .map(ModelInfo::id).collect(Collectors.toSet());
            List<String> faltando = Stream.of(
                            form == null ? null : form.modeloModeracao(),
                            form == null ? null : form.modeloChat(),
                            form == null ? null : form.modeloProntuario())
                    .filter(StringUtils::hasText).map(String::trim).distinct()
                    .filter(m -> !ids.contains(m)).toList();
            if (!faltando.isEmpty()) {
                return new ResultadoTeste(true,
                        "Chave OK, mas estes modelos não foram encontrados na conta: " + String.join(", ", faltando));
            }
            return new ResultadoTeste(true, "Conexão OK — chave válida.");
        } catch (RuntimeException e) {
            String causa = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ResultadoTeste(false, "Falha ao validar a chave: " + causa);
        } finally {
            fechar(c);
        }
    }

    public record ResultadoTeste(boolean ok, String mensagem) {
    }

    // ===================== Internos =====================

    private Optional<ConfiguracaoIa> carregar(EscopoIa escopo) {
        return escopo.tipo() == EscopoIa.Tipo.UNIDADE
                ? repo.findByUnidadeId(escopo.unidadeId())
                : repo.findByUnidadeIdIsNull();
    }

    private String apiKeySalvada(EscopoIa escopo) {
        return carregar(escopo).map(c -> cripto.decifrar(c.getApiKey())).orElse(null);
    }

    /** Exige um modelo preenchido e que exista ATIVO no cadastro "Modelos de IA"; devolve o id já trimado. */
    private String exigirModeloAtivo(String valor, String frente) {
        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Escolha o modelo " + frente + ".");
        }
        String id = valor.trim();
        boolean ativo = modeloIaRepository.findByModeloId(id).filter(ModeloIa::isAtivo).isPresent();
        if (!ativo) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O modelo " + frente + " (" + id + ") não existe ou está inativo no cadastro de Modelos de IA.");
        }
        return id;
    }

    private static void fechar(AnthropicClient c) {
        try {
            c.close();
        } catch (Exception ignore) {
            // best-effort ao fechar o client
        }
    }
}
