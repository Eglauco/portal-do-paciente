package com.example.pop.conselho;

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
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;

@RestController
@RequestMapping("/conselho")
public class ConselhoController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "sigla", "sigla",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    private final ConselhoRepository repository;
    private final ExportacaoService exportacaoService;

    public ConselhoController(ConselhoRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    /**
     * Lista conselhos de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Conselho> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<Conselho> resultado = repository.search(codigo, filtroNome, pageable);

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
     * Exporta os conselhos que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por nome.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        List<Conselho> dados = repository.search(codigo, filtroNome,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<Conselho>> cols = ExportacaoService.filtrar(colunasConselho(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Conselhos", filtrosConselho(codigo, filtroNome), cols, dados)
                : exportacaoService.excel("Conselhos", cols, dados);
        String nomeArquivo = "conselhos-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasConselho().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosConselho(Long codigo, String nome) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome != null && !nome.isBlank()) ? nome : "Todos"));
    }

    private static List<ColunaExport<Conselho>> colunasConselho() {
        return List.of(
                ColunaExport.de("Código", c -> c.getId() == null ? "" : String.valueOf(c.getId())),
                ColunaExport.de("Sigla", Conselho::getSigla),
                ColunaExport.de("Nome", Conselho::getNome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conselho> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conselho criar(@RequestBody Conselho conselho) {
        conselho.setId(null);
        conselho.setSigla(obrigatorio(conselho.getSigla(), "a sigla", 20));
        conselho.setNome(obrigatorio(conselho.getNome(), "o nome", 120));
        return repository.save(conselho);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Conselho> atualizar(@PathVariable Long id, @RequestBody Conselho conselho) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setSigla(obrigatorio(conselho.getSigla(), "a sigla", 20));
                    existente.setNome(obrigatorio(conselho.getNome(), "o nome", 120));
                    return ResponseEntity.ok(repository.save(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Trim + obrigatório (422 se vazio) + limite de tamanho (422 se exceder). */
    private static String obrigatorio(String valor, String rotulo, int max) {
        String limpo = (valor == null) ? "" : valor.trim();
        if (limpo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe " + rotulo + ".");
        }
        if (limpo.length() > max) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O campo excede o máximo de " + max + " caracteres.");
        }
        return limpo;
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
