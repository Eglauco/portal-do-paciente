package com.example.pop.unidade;

import java.time.LocalDate;
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

@RestController
@RequestMapping("/unidade")
public class UnidadeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
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
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenadas por código (id).
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome) {
        String filtroNome = (nome == null) ? "" : nome.trim();

        List<Unidade> dados = repository.search(codigo, filtroNome, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Unidade::getId))
                .toList();
        List<ColunaExport<Unidade>> colunas = colunasUnidade();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Unidades", filtrosUnidade(codigo, filtroNome), colunas, dados)
                : exportacaoService.excel("Unidades", colunas, dados);
        String nomeArquivo = "unidades-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
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
    public ResponseEntity<Unidade> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Unidade criar(@RequestBody Unidade unidade) {
        unidade.setId(null);
        return repository.save(unidade);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Unidade> atualizar(@PathVariable Long id, @RequestBody Unidade unidade) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(unidade.getNome());
                    return ResponseEntity.ok(repository.save(existente));
                })
                .orElse(ResponseEntity.notFound().build());
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
