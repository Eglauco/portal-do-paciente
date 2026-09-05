package com.example.pop.sau;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;

import jakarta.validation.Valid;

/** Cadastro (CRUD) dos tipos de manifestação do SAU — back-office (admin). */
@RestController
@RequestMapping("/tipo-manifestacao")
public class TipoManifestacaoController {

    private static final int TAMANHO_MAXIMO = 100;

    private final TipoManifestacaoRepository repository;
    private final ManifestacaoRepository manifestacaoRepository;
    private final ExportacaoService exportacaoService;

    public TipoManifestacaoController(TipoManifestacaoRepository repository,
            ManifestacaoRepository manifestacaoRepository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.manifestacaoRepository = manifestacaoRepository;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<TipoManifestacaoResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Sort.by(Sort.Direction.ASC, "nome"));
        Page<TipoManifestacao> resultado = repository.search(nome == null ? "" : nome.trim(), ativo, pageable);
        List<TipoManifestacaoResponse> content = resultado.getContent().stream()
                .map(TipoManifestacaoResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta os tipos de manifestação que batem com os MESMOS filtros da tela
     * (todos os registros, sem paginação) em Excel (padrão) ou PDF. Ordem alfabética.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) List<String> colunas) {
        List<TipoManifestacao> dados = repository.search(nome == null ? "" : nome.trim(), ativo, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(TipoManifestacao::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ColunaExport<TipoManifestacao>> cols = ExportacaoService.filtrar(colunasTipoManifestacao(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Tipos de manifestação", filtrosTipoManifestacao(nome, ativo), cols, dados)
                : exportacaoService.excel("Tipos de manifestação", cols, dados);
        String nomeArquivo = "tipos-manifestacao-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasTipoManifestacao().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosTipoManifestacao(String nome, Boolean ativo) {
        String situacao = ativo == null ? "Todas" : (ativo ? "Ativos" : "Inativos");
        return List.of(
                new FiltroAplicado("Nome", nome == null || nome.isBlank() ? "Todos" : nome.trim()),
                new FiltroAplicado("Situação", situacao));
    }

    private static List<ColunaExport<TipoManifestacao>> colunasTipoManifestacao() {
        return List.of(
                ColunaExport.de("Código", t -> String.valueOf(t.getId())),
                ColunaExport.de("Nome", TipoManifestacao::getNome),
                ColunaExport.de("Descrição", t -> t.getDescricao() == null ? "" : t.getDescricao()),
                ColunaExport.de("Situação", t -> t.isAtivo() ? "Ativo" : "Inativo"),
                ColunaExport.de("Criado em", t -> t.getCriadoEm() == null ? "" : t.getCriadoEm().format(DATA_HORA)),
                ColunaExport.de("Atualizado em",
                        t -> t.getAtualizadoEm() == null ? "" : t.getAtualizadoEm().format(DATA_HORA)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoManifestacaoResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(t -> ResponseEntity.ok(TipoManifestacaoResponse.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoManifestacaoResponse criar(@Valid @RequestBody TipoManifestacaoRequest dados) {
        LocalDateTime agora = LocalDateTime.now();
        TipoManifestacao tipo = new TipoManifestacao();
        tipo.setNome(dados.nome().trim());
        tipo.setDescricao(descricaoLimpa(dados.descricao()));
        tipo.setAtivo(dados.ativo() == null || dados.ativo());
        tipo.setCriadoEm(agora);
        tipo.setAtualizadoEm(agora);
        return TipoManifestacaoResponse.from(repository.save(tipo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoManifestacaoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody TipoManifestacaoRequest dados) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(dados.nome().trim());
                    existente.setDescricao(descricaoLimpa(dados.descricao()));
                    if (dados.ativo() != null) {
                        existente.setAtivo(dados.ativo());
                    }
                    existente.setAtualizadoEm(LocalDateTime.now());
                    return ResponseEntity.ok(TipoManifestacaoResponse.from(repository.save(existente)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Exclui um tipo. Se já houver manifestações usando-o, bloqueia (desative em vez de excluir). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (manifestacaoRepository.existsByTipoId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String descricaoLimpa(String descricao) {
        if (descricao == null) {
            return null;
        }
        String limpa = descricao.trim();
        return limpa.isEmpty() ? null : limpa;
    }
}
