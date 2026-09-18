package com.example.pop.unidade;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;

@RestController
@RequestMapping("/unidade")
public class UnidadeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    private final UnidadeRepository repository;
    private final ExportacaoService exportacaoService;

    public UnidadeController(UnidadeRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    /**
     * Lista unidades de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Unidade> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<Unidade> resultado = repository.search(codigo, filtroNome, pageable);

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /**
     * Exporta as unidades que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenadas por nome.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();

        // Ordena no BANCO (mesma collation do grid) para o export bater com a tela.
        List<Unidade> dados = repository.search(codigo, filtroNome,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<Unidade>> cols = ExportacaoService.filtrar(colunasUnidade(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Unidades", filtrosUnidade(codigo, filtroNome), cols, dados)
                : exportacaoService.excel("Unidades", cols, dados);
        String nomeArquivo = "unidades-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasUnidade().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosUnidade(Long codigo, String nome) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome == null || nome.isBlank()) ? "Todos" : nome));
    }

    private static List<ColunaExport<Unidade>> colunasUnidade() {
        return List.of(
                ColunaExport.de("Código", u -> u.getId() == null ? "" : String.valueOf(u.getId())),
                ColunaExport.de("Nome", Unidade::getNome));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<UnidadeResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(UnidadeResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UnidadeResponse criar(@RequestBody UnidadeRequest req) {
        Unidade unidade = new Unidade();
        unidade.setNome(req.nome());
        aplicarFaq(unidade, req);
        return UnidadeResponse.from(repository.save(unidade));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UnidadeResponse> atualizar(@PathVariable Long id, @RequestBody UnidadeRequest req) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(req.nome());
                    aplicarFaq(existente, req);
                    return ResponseEntity.ok(UnidadeResponse.from(repository.save(existente)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Reconstrói o FAQ da unidade pela PRÓPRIA coleção (orphanRemoval): limpa e re-adiciona na ordem
     * enviada. Nunca troca a referência da coleção (senão o Hibernate perde o rastreio dos órfãos).
     * Linhas sem pergunta ou sem resposta são ignoradas.
     */
    private static void aplicarFaq(Unidade unidade, UnidadeRequest req) {
        unidade.getFaq().clear();
        if (req.faq() == null) {
            return;
        }
        int ordem = 0;
        for (UnidadeRequest.FaqItem item : req.faq()) {
            if (item == null) {
                continue;
            }
            String pergunta = item.pergunta() == null ? "" : item.pergunta().trim();
            String resposta = item.resposta() == null ? "" : item.resposta().trim();
            if (pergunta.isBlank() || resposta.isBlank()) {
                continue;
            }
            UnidadeFaq faq = new UnidadeFaq();
            faq.setUnidade(unidade);
            faq.setPergunta(pergunta);
            faq.setResposta(resposta);
            faq.setOrdem(ordem++);
            unidade.getFaq().add(faq);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
