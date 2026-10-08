package com.example.pop.configuracaoagenda;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.assinatura.AssinaturaProviderFactory;
import com.example.pop.storage.StorageService;
import com.example.pop.zapsign.ZapSignClient;

import jakarta.validation.Valid;

/**
 * CRUD dos documentos de Termo de Consentimento (TCLE) de um configuracaoAgenda (back-office).
 * Sob /configuracaoAgenda/** → ADMIN.
 *
 * <p>Duas origens de modelo (ver {@link OrigemModeloTermo}): {@code ARQUIVO} (o .docx nosso, no S3 pasta
 * "tcle", serve qualquer provedor) ou {@code ZAPSIGN_MODELO} (modelo já pronto no ZapSign — só o ZapSign
 * assina). Os endpoints {@code /zapsign/modelos} listam/detalham os modelos do ZapSign para o seletor da tela.
 */
@RestController
@RequestMapping("/configuracao-agenda")
public class TermoConfiguracaoAgendaController {

    private final TermoConfiguracaoAgendaRepository repository;
    private final ConfiguracaoAgendaRepository configuracaoAgendaRepository;
    private final StorageService storageService;
    private final AssinaturaProviderFactory providerFactory;
    private final ZapSignClient zapSignClient;

    public TermoConfiguracaoAgendaController(TermoConfiguracaoAgendaRepository repository,
            ConfiguracaoAgendaRepository configuracaoAgendaRepository, StorageService storageService,
            AssinaturaProviderFactory providerFactory, ZapSignClient zapSignClient) {
        this.repository = repository;
        this.configuracaoAgendaRepository = configuracaoAgendaRepository;
        this.storageService = storageService;
        this.providerFactory = providerFactory;
        this.zapSignClient = zapSignClient;
    }

    /**
     * Catálogo das variáveis dinâmicas que o backend substitui no Word ao enviar para a assinatura.
     * Fonte única (enum {@link VariavelTermo}); a tela mostra estes tokens para o admin copiar no Word.
     */
    @GetMapping("/termos/variaveis")
    public List<VariavelTermoResponse> variaveis() {
        return Arrays.stream(VariavelTermo.values()).map(VariavelTermoResponse::from).toList();
    }

    /** Modelos (templates) prontos no ZapSign — para o admin selecionar no cadastro do termo. */
    @GetMapping("/zapsign/modelos")
    public List<ZapSignClient.ModeloResumo> modelosZapSign() {
        return zapSignClient.listarModelos();
    }

    /** Variável do modelo do ZapSign + se o nosso resolver sabe preenchê-la ({@code conhecida}). */
    public record VariavelModeloResponse(String variable, String label, boolean required, boolean conhecida) {
    }

    /** Detalhe de um modelo do ZapSign: nome + variáveis, marcando quais o POP sabe preencher. */
    public record ModeloZapSignDetalheResponse(String token, String nome, List<VariavelModeloResponse> variaveis) {
    }

    /** Detalha um modelo do ZapSign, marcando cada variável como conhecida (o POP preenche) ou não. */
    @GetMapping("/zapsign/modelos/{token}")
    public ModeloZapSignDetalheResponse detalharModeloZapSign(@PathVariable String token) {
        Set<String> conhecidos = Arrays.stream(VariavelTermo.values())
                .map(VariavelTermo::token).collect(Collectors.toSet());
        ZapSignClient.ModeloDetalhe d = zapSignClient.detalharModelo(token);
        List<VariavelModeloResponse> vars = d.variaveis().stream()
                .map(v -> new VariavelModeloResponse(v.variable(), v.label(), v.required(),
                        v.variable() != null && conhecidos.contains(v.variable())))
                .toList();
        return new ModeloZapSignDetalheResponse(d.token(), d.nome(), vars);
    }

    /** Documentos TCLE de um configuracaoAgenda (mais recentes primeiro). */
    @GetMapping("/{configuracaoAgendaId}/termos")
    public List<TermoConfiguracaoAgendaResponse> listar(@PathVariable Long configuracaoAgendaId) {
        return repository.findByConfiguracaoAgendaIdOrderByCriadoEmDesc(configuracaoAgendaId)
                .stream().map(TermoConfiguracaoAgendaResponse::from).toList();
    }

    @PostMapping("/{configuracaoAgendaId}/termos")
    @ResponseStatus(HttpStatus.CREATED)
    public TermoConfiguracaoAgendaResponse criar(@PathVariable Long configuracaoAgendaId,
            @Valid @RequestBody TermoConfiguracaoAgendaRequest request) {
        ConfiguracaoAgenda configuracaoAgenda = configuracaoAgendaRepository.findById(configuracaoAgendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuração da Agenda não encontrada"));
        TermoConfiguracaoAgenda t = new TermoConfiguracaoAgenda();
        t.setConfiguracaoAgenda(configuracaoAgenda);
        t.setNome(request.nome().trim());
        aplicarOrigem(t, request, null);
        t.setCriadoEm(LocalDateTime.now());
        return TermoConfiguracaoAgendaResponse.from(repository.save(t));
    }

    /** Substitui/renomeia/troca a origem de um documento. Se o arquivo antigo saiu de cena, remove do S3. */
    @PutMapping("/termos/{id}")
    public ResponseEntity<TermoConfiguracaoAgendaResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody TermoConfiguracaoAgendaRequest request) {
        return repository.findById(id)
                .map(t -> {
                    String urlAntiga = t.getUrl();
                    t.setNome(request.nome().trim());
                    aplicarOrigem(t, request, urlAntiga);
                    TermoConfiguracaoAgenda salvo = repository.save(t);
                    // O arquivo antigo saiu (troca de arquivo OU virou modelo ZapSign): apaga do S3 (best-effort).
                    if (urlAntiga != null && !urlAntiga.equals(salvo.getUrl())) {
                        excluirNoS3(urlAntiga);
                    }
                    return ResponseEntity.ok(TermoConfiguracaoAgendaResponse.from(salvo));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/termos/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        return repository.findById(id)
                .map(t -> {
                    repository.delete(t);
                    excluirNoS3(t.getUrl());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Aplica a origem escolhida ao termo, validando o que cada uma exige:
     * <ul>
     *   <li>{@code ZAPSIGN_MODELO}: exige o token do modelo selecionado; zera o arquivo (url/content-type).</li>
     *   <li>{@code ARQUIVO}: exige a url na pasta "tcle"; (re)registra o Modelo no provedor ativo quando o
     *       arquivo é novo/trocado (habilita as variáveis dinâmicas).</li>
     * </ul>
     * {@code urlAntiga} = url antes da alteração (null no cadastro) — usada para decidir o re-registro.
     */
    private void aplicarOrigem(TermoConfiguracaoAgenda t, TermoConfiguracaoAgendaRequest request, String urlAntiga) {
        t.setProfissionalAssina(request.profissionalAssinaEfetivo());
        t.setProfissionalCertificado(request.profissionalCertificadoEfetivo());
        OrigemModeloTermo origem = request.origemEfetiva();
        t.setOrigemModelo(origem);
        if (origem == OrigemModeloTermo.ZAPSIGN_MODELO) {
            String token = request.providerTemplateToken() == null ? "" : request.providerTemplateToken().trim();
            if (token.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um modelo do ZapSign.");
            }
            t.setUrl(null);
            t.setContentType(null);
            t.setProviderTemplateToken(token);
            t.setModeloProviderNome(request.modeloProviderNome() == null ? null : request.modeloProviderNome().trim());
            return;
        }
        // ARQUIVO
        exigirUrlNaPastaTcle(request.url());
        boolean arquivoNovoOuTrocado = urlAntiga == null || !urlAntiga.equals(request.url());
        t.setUrl(request.url());
        t.setContentType(request.contentType());
        t.setModeloProviderNome(null);
        if (arquivoNovoOuTrocado) {
            // Novo arquivo (ou trocado): re-registra o Modelo no provedor ativo (novo token, ou null).
            t.setProviderTemplateToken(registrarModeloSeDocx(t.getNome(), t.getUrl(), t.getContentType()));
        }
    }

    /**
     * Registra o Word (.docx) como Modelo no provedor ATIVO e devolve o token (ou null). Best-effort — não
     * bloqueia o cadastro. Provedor que renderiza local (Autentique) devolve null (usa o próprio .docx ao assinar).
     */
    private String registrarModeloSeDocx(String nome, String url, String contentType) {
        if (!ehDocx(url, contentType)) {
            return null; // .doc / outro formato: não vira Modelo (não terá substituição de variáveis)
        }
        AssinaturaProvider provider = providerFactory.ativo();
        if (!provider.usaModeloRemoto()) {
            return null; // provedor renderiza o documento localmente (sem template remoto)
        }
        try {
            byte[] bytes = storageService.baixarBytes(url);
            if (bytes == null) {
                return null;
            }
            return provider.registrarModelo(nome, bytes);
        } catch (RuntimeException e) {
            return null; // não bloqueia o cadastro; re-registrável ao substituir o arquivo ou ao assinar
        }
    }

    /** true quando o arquivo é .docx (Word moderno) — único formato aceito como Modelo/renderização. */
    private static boolean ehDocx(String url, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("wordprocessingml")) {
            return true;
        }
        return url != null && url.toLowerCase().endsWith(".docx");
    }

    /** Defesa: a URL precisa pertencer à pasta "tcle" (o cliente não pode gravar URL de outra pasta). */
    private void exigirUrlNaPastaTcle(String url) {
        if (!storageService.urlNaPasta(url, "tcle")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo do termo inválido.");
        }
    }

    /** Remove o objeto no S3 sem propagar falha (a limpeza não pode impedir a operação). */
    private void excluirNoS3(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            storageService.excluirPorUrl(url);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }
}
