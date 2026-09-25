package com.example.pop.procedimento;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

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

import com.example.pop.storage.StorageService;
import com.example.pop.zapsign.ZapSignClient;

import jakarta.validation.Valid;

/**
 * CRUD dos documentos de Termo de Consentimento (TCLE) de um procedimento (back-office).
 * Sob /procedimento/** → ADMIN. O arquivo Word vive no S3 (pasta "tcle"); ao substituir/excluir,
 * o objeto antigo é removido do S3 (best-effort).
 */
@RestController
@RequestMapping("/procedimento")
public class TermoProcedimentoController {

    private final TermoProcedimentoRepository repository;
    private final ProcedimentoRepository procedimentoRepository;
    private final StorageService storageService;
    private final ZapSignClient zapSign;

    public TermoProcedimentoController(TermoProcedimentoRepository repository,
            ProcedimentoRepository procedimentoRepository, StorageService storageService, ZapSignClient zapSign) {
        this.repository = repository;
        this.procedimentoRepository = procedimentoRepository;
        this.storageService = storageService;
        this.zapSign = zapSign;
    }

    /**
     * Catálogo das variáveis dinâmicas que o backend substitui no Word ao enviar para a assinatura.
     * Fonte única (enum {@link VariavelTermo}); a tela mostra estes tokens para o admin copiar no Word.
     */
    @GetMapping("/termos/variaveis")
    public List<VariavelTermoResponse> variaveis() {
        return Arrays.stream(VariavelTermo.values()).map(VariavelTermoResponse::from).toList();
    }

    /** Documentos TCLE de um procedimento (mais recentes primeiro). */
    @GetMapping("/{procedimentoId}/termos")
    public List<TermoProcedimentoResponse> listar(@PathVariable Long procedimentoId) {
        return repository.findByProcedimentoIdOrderByCriadoEmDesc(procedimentoId)
                .stream().map(TermoProcedimentoResponse::from).toList();
    }

    @PostMapping("/{procedimentoId}/termos")
    @ResponseStatus(HttpStatus.CREATED)
    public TermoProcedimentoResponse criar(@PathVariable Long procedimentoId,
            @Valid @RequestBody TermoProcedimentoRequest request) {
        Procedimento procedimento = procedimentoRepository.findById(procedimentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado"));
        exigirUrlNaPastaTcle(request.url());
        TermoProcedimento t = new TermoProcedimento();
        t.setProcedimento(procedimento);
        t.setNome(request.nome().trim());
        t.setUrl(request.url());
        t.setContentType(request.contentType());
        // Registra o .docx como Modelo na ZapSign no upload (habilita as variáveis dinâmicas).
        t.setZapsignTemplateToken(registrarModeloSeDocx(t.getNome(), t.getUrl(), t.getContentType()));
        t.setCriadoEm(LocalDateTime.now());
        return TermoProcedimentoResponse.from(repository.save(t));
    }

    /** Substitui/renomeia um documento. Se o arquivo (URL) mudou, remove o antigo do S3. */
    @PutMapping("/termos/{id}")
    public ResponseEntity<TermoProcedimentoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody TermoProcedimentoRequest request) {
        return repository.findById(id)
                .map(t -> {
                    exigirUrlNaPastaTcle(request.url());
                    String urlAntiga = t.getUrl();
                    boolean arquivoMudou = urlAntiga == null || !urlAntiga.equals(request.url());
                    t.setNome(request.nome().trim());
                    t.setUrl(request.url());
                    t.setContentType(request.contentType());
                    if (arquivoMudou) {
                        // Trocou o arquivo: re-registra o Modelo na ZapSign (novo token).
                        t.setZapsignTemplateToken(registrarModeloSeDocx(t.getNome(), t.getUrl(), t.getContentType()));
                    }
                    TermoProcedimento salvo = repository.save(t);
                    if (arquivoMudou && urlAntiga != null) {
                        excluirNoS3(urlAntiga); // trocou o arquivo: apaga o anterior (best-effort)
                    }
                    return ResponseEntity.ok(TermoProcedimentoResponse.from(salvo));
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

    /** Registra o Word como Modelo na ZapSign quando é .docx; devolve o token (ou null). Best-effort. */
    private String registrarModeloSeDocx(String nome, String url, String contentType) {
        if (!ehDocx(url, contentType)) {
            return null; // .doc / outro formato: não vira Modelo (não terá substituição de variáveis)
        }
        try {
            byte[] bytes = storageService.baixarBytes(url);
            if (bytes == null) {
                return null;
            }
            return zapSign.criarTemplate(nome, Base64.getEncoder().encodeToString(bytes));
        } catch (RuntimeException e) {
            return null; // não bloqueia o cadastro; re-registrável ao substituir o arquivo
        }
    }

    /** true quando o arquivo é .docx (Word moderno) — único formato que a ZapSign aceita como Modelo. */
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
