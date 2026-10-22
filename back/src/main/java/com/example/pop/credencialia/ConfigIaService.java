package com.example.pop.credencialia;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

import com.example.pop.configuracao.SegredoCripto;
import com.example.pop.modeloia.ModeloIa;
import com.example.pop.modeloia.ModeloIaRepository;
import com.example.pop.tenant.TenantContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Resolve a credencial de IA por ESCOPO (unidade / padrão do inquilino), decifrando a api-key, e administra
 * o cadastro (CRUD write-only + testar). É MULTI-PROVEDOR: cada escopo tem um {@link TipoProvedorIa} (a
 * unidade pode usar um provedor e o inquilino outro) e o {@link ProvedorIaFactory} monta o {@link ChatModel}
 * (LangChain4j) certo. É "nunca global": sem credencial, {@link #gerar} vem vazio e cada frente degrada.
 *
 * <p>UNIDADE sem config própria HERDA o padrão do inquilino; config própria INATIVA bloqueia (não herda).
 * Cacheia a credencial resolvida por {@code schema|escopo} e os ChatModels por
 * {@code schema|escopo|modelo|maxTokens|timeout}, invalidados ao salvar/remover (via evento no commit).
 */
@Service
public class ConfigIaService {

    private final ConfiguracaoIaRepository repo;
    private final ModeloIaRepository modeloIaRepository;
    private final SegredoCripto cripto;
    private final ApplicationEventPublisher eventos;
    private final ProvedorIaFactory factory;

    /**
     * Modelos padrão da plataforma (env) — fallback DEFENSIVO em {@link #modeloResolvido} caso uma credencial
     * antiga tenha chegado com o modelo em branco. No cadastro o modelo é obrigatório (ver {@link #salvar}).
     */
    private final String modeloModeracaoPadrao;
    private final String modeloChatPadrao;
    private final String modeloProntuarioPadrao;

    private final ConcurrentHashMap<String, CredenciaisIa> cacheCred = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatModel> cacheModelo = new ConcurrentHashMap<>();

    public ConfigIaService(
            ConfiguracaoIaRepository repo,
            ModeloIaRepository modeloIaRepository,
            SegredoCripto cripto,
            ApplicationEventPublisher eventos,
            ProvedorIaFactory factory,
            @Value("${anthropic.moderacao.modelo:claude-haiku-4-5}") String modeloModeracaoPadrao,
            @Value("${anthropic.chat.modelo:claude-haiku-4-5}") String modeloChatPadrao,
            @Value("${anthropic.prontuario.modelo:claude-opus-5}") String modeloProntuarioPadrao) {
        this.repo = repo;
        this.modeloIaRepository = modeloIaRepository;
        this.cripto = cripto;
        this.eventos = eventos;
        this.factory = factory;
        this.modeloModeracaoPadrao = modeloModeracaoPadrao;
        this.modeloChatPadrao = modeloChatPadrao;
        this.modeloProntuarioPadrao = modeloProntuarioPadrao;
    }

    // ===================== Geração (usada pelas frentes de IA) =====================

    /**
     * Gera uma resposta de IA no escopo/frente, pelo provedor configurado. Vazio quando não há credencial
     * (não configurada/inativa) — a frente degrada como hoje (sem chamar a IA). Lança em erro da API da IA
     * (a frente trata com seu try/catch — fail-open/closed).
     */
    public Optional<GeracaoIa> gerar(EscopoIa escopo, FrenteIa frente, RequisicaoIa req) {
        CredenciaisIa cred = resolver(escopo).orElse(null);
        if (cred == null || !StringUtils.hasText(cred.apiKey())) {
            return Optional.empty();
        }
        String modelo = modeloResolvido(cred, frente);
        ChatModel model = obterModelo(escopo, cred, modelo, req.maxTokens(), req.timeout());
        ChatResponse resp = model.chat(montarMensagens(req));
        String texto = resp.aiMessage() == null || resp.aiMessage().text() == null ? "" : resp.aiMessage().text();
        TokenUsage uso = resp.tokenUsage();
        long entrada = uso != null && uso.inputTokenCount() != null ? uso.inputTokenCount() : 0L;
        long saida = uso != null && uso.outputTokenCount() != null ? uso.outputTokenCount() : 0L;
        return Optional.of(new GeracaoIa(texto, entrada, saida, modelo));
    }

    /** Traduz a requisição provider-agnostic para as mensagens do LangChain4j (anexo vira PDF/imagem). */
    private List<ChatMessage> montarMensagens(RequisicaoIa req) {
        List<ChatMessage> mensagens = new ArrayList<>();
        if (StringUtils.hasText(req.system())) {
            mensagens.add(SystemMessage.from(req.system()));
        }
        for (Turno t : req.turnos()) {
            String texto = t.texto() == null ? "" : t.texto();
            if (t.papel() == Turno.Papel.ASSISTENTE) {
                mensagens.add(AiMessage.from(texto));
            } else if (t.anexo() != null) {
                String base64 = Base64.getEncoder().encodeToString(t.anexo().bytes());
                Content documento = t.anexo().ehPdf()
                        ? PdfFileContent.from(base64, t.anexo().mimeType())
                        : ImageContent.from(base64, t.anexo().mimeType());
                // Documento ANTES do texto (recomendação para análise multimodal).
                mensagens.add(UserMessage.from(documento, TextContent.from(texto)));
            } else {
                mensagens.add(UserMessage.from(texto));
            }
        }
        return mensagens;
    }

    private ChatModel obterModelo(EscopoIa escopo, CredenciaisIa cred, String modelo, long maxTokens, Duration timeout) {
        String chave = TenantContext.atual() + "|" + escopo.cacheId()
                + "|" + modelo + "|" + maxTokens + "|" + timeout.toSeconds();
        return cacheModelo.computeIfAbsent(chave,
                k -> factory.montar(cred.provedor(), cred.apiKey(), cred.baseUrl(), modelo, maxTokens, timeout));
    }

    private String modeloResolvido(CredenciaisIa cred, FrenteIa frente) {
        String doCliente = cred.modeloDa(frente);
        if (StringUtils.hasText(doCliente)) {
            return doCliente.trim();
        }
        return switch (frente) {
            case MODERACAO -> modeloModeracaoPadrao;
            case CHAT -> modeloChatPadrao;
            case PRONTUARIO -> modeloProntuarioPadrao;
        };
    }

    // ===================== Resolução da credencial =====================

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
        return new CredenciaisIa(c.getProvedor(), cripto.decifrar(c.getApiKey()), c.getBaseUrl(),
                c.getModeloModeracao(), c.getModeloChat(), c.getModeloProntuario());
    }

    // ===================== Cadastro (tela) =====================

    /** Provedores oferecidos na credencial: os SUPORTADOS pela arquitetura que já têm ≥1 modelo ativo cadastrado. */
    public List<TipoProvedorIa> provedoresDisponiveis() {
        return modeloIaRepository.provedoresComModeloAtivo().stream()
                .filter(factory::suportado)
                .sorted()
                .toList();
    }

    public StatusIa status(EscopoIa escopo) {
        return carregar(escopo)
                .map(c -> new StatusIa(true, StringUtils.hasText(c.getApiKey()), c.getProvedor().name(), c.getBaseUrl(),
                        c.getModeloModeracao(), c.getModeloChat(), c.getModeloProntuario(), c.isAtivo()))
                .orElseGet(() -> new StatusIa(false, false, TipoProvedorIa.ANTHROPIC.name(), null, "", "", "", true));
    }

    @Transactional
    public void salvar(EscopoIa escopo, FormIa form) {
        Long unidadeId = escopo.tipo() == EscopoIa.Tipo.UNIDADE ? escopo.unidadeId() : null;
        TipoProvedorIa provedor = TipoProvedorIa.de(form.provedor());
        if (!factory.suportado(provedor)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Provedor de IA '" + provedor + "' ainda não está disponível.");
        }
        Optional<ConfiguracaoIa> existente = carregar(escopo);
        if (existente.isEmpty() && !StringUtils.hasText(form.apiKey())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a API key do provedor.");
        }
        LocalDateTime agora = LocalDateTime.now();
        ConfiguracaoIa c = existente.orElseGet(() -> {
            ConfiguracaoIa nova = new ConfiguracaoIa();
            nova.setUnidadeId(unidadeId);
            nova.setCriadoEm(agora);
            return nova;
        });
        c.setProvedor(provedor);
        c.setBaseUrl(StringUtils.hasText(form.baseUrl()) ? form.baseUrl().trim() : null);
        // API key em branco MANTÉM a atual (write-only); preenchida → cifra a nova.
        if (StringUtils.hasText(form.apiKey())) {
            c.setApiKey(cripto.cifrar(form.apiKey().trim()));
        }
        // Modelos obrigatórios + validados contra o cadastro "Modelos de IA" (ativo E do provedor) → custo sempre computa.
        c.setModeloModeracao(exigirModeloAtivo(form.modeloModeracao(), "da moderação", provedor));
        c.setModeloChat(exigirModeloAtivo(form.modeloChat(), "do chat", provedor));
        c.setModeloProntuario(exigirModeloAtivo(form.modeloProntuario(), "do prontuário", provedor));
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

    /** Invalida os caches do schema alterado (credenciais + ChatModels), depois do commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterar(ConfigIaAlteradaEvento ev) {
        String pref = ev.schema() + "|";
        cacheCred.keySet().removeIf(k -> k.startsWith(pref));
        cacheModelo.keySet().removeIf(k -> k.startsWith(pref));
    }

    // ===================== Testar a chave =====================

    /**
     * Testa a api-key do formulário (em branco usa a salva). Prova CADA modelo escolhido com uma geração
     * mínima ({@code max_tokens=1}) via o provedor escolhido: é a forma confiável de saber se a CHAVE
     * consegue USAR aquele modelo. Gasta pouquíssimos tokens (1 por modelo distinto).
     */
    public ResultadoTeste testar(EscopoIa escopo, FormIa form) {
        TipoProvedorIa provedor = TipoProvedorIa.de(form == null ? null : form.provedor());
        if (!factory.suportado(provedor)) {
            return new ResultadoTeste(false, "Provedor de IA '" + provedor + "' ainda não está disponível.");
        }
        String apiKey = StringUtils.hasText(form == null ? null : form.apiKey())
                ? form.apiKey().trim()
                : apiKeySalvada(escopo);
        if (!StringUtils.hasText(apiKey)) {
            return new ResultadoTeste(false, "Informe a API key para testar.");
        }
        String baseUrl = form != null && StringUtils.hasText(form.baseUrl()) ? form.baseUrl().trim() : baseUrlSalvada(escopo);
        List<String> modelos = Stream.of(
                        form == null ? null : form.modeloModeracao(),
                        form == null ? null : form.modeloChat(),
                        form == null ? null : form.modeloProntuario())
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (modelos.isEmpty()) {
            return new ResultadoTeste(false, "Escolha os modelos para testar.");
        }
        // Probe com um orçamento pequeno PORÉM suficiente (24): alguns provedores (ex.: Gemini) devolvem
        // resposta vazia com max=1 e a lib trata como falha. Ainda é custo irrisório (1 chamada por modelo).
        List<String> falharam = new ArrayList<>();
        String primeiroErro = null;
        for (String modelo : modelos) {
            try {
                ChatModel m = factory.montar(provedor, apiKey, baseUrl, modelo, 24L, Duration.ofSeconds(25));
                m.chat("oi");
            } catch (AuthenticationException e) {
                return new ResultadoTeste(false, "Chave inválida ou sem permissão no provedor.");
            } catch (RuntimeException e) {
                falharam.add(modelo);
                if (primeiroErro == null) {
                    primeiroErro = causaRaiz(e);
                }
            }
        }
        if (!falharam.isEmpty()) {
            return new ResultadoTeste(true,
                    "Chave OK, mas a geração falhou em: " + String.join(", ", falharam)
                            + (StringUtils.hasText(primeiroErro) ? " — " + primeiroErro : "")
                            + ". Verifique o acesso ao modelo na conta do provedor.");
        }
        return new ResultadoTeste(true, "Conexão OK — chave e modelos válidos.");
    }

    /** Mensagem da causa mais profunda (o provedor costuma embrulhar o erro real), truncada p/ exibir. */
    private String causaRaiz(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String msg = raiz.getMessage();
        if (!StringUtils.hasText(msg)) {
            msg = raiz.getClass().getSimpleName();
        }
        msg = msg.replaceAll("\\s+", " ").trim();
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
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

    private String baseUrlSalvada(EscopoIa escopo) {
        return carregar(escopo).map(ConfiguracaoIa::getBaseUrl).orElse(null);
    }

    /** Exige um modelo preenchido, ATIVO e DO PROVEDOR no cadastro "Modelos de IA"; devolve o id já trimado. */
    private String exigirModeloAtivo(String valor, String frente, TipoProvedorIa provedor) {
        if (!StringUtils.hasText(valor)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Escolha o modelo " + frente + ".");
        }
        String id = valor.trim();
        boolean ok = modeloIaRepository.findByModeloId(id)
                .filter(ModeloIa::isAtivo)
                .filter(m -> m.getProvedor() == provedor)
                .isPresent();
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O modelo " + frente + " (" + id + ") não existe, está inativo, ou não é do provedor "
                            + provedor + " no cadastro de Modelos de IA.");
        }
        return id;
    }
}
