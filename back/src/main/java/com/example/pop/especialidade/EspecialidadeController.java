package com.example.pop.especialidade;

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
@RequestMapping("/especialidade")
public class EspecialidadeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final EspecialidadeRepository repository;
    private final ExportacaoService exportacaoService;

    public EspecialidadeController(EspecialidadeRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    /**
     * Lista especialidades de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Especialidade> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Especialidade> resultado = repository.search(codigo, filtroNome, pageable);

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
     * Exporta as especialidades que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenadas por código (id).
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        List<Especialidade> dados = repository.search(codigo, filtroNome, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Especialidade::getId))
                .toList();
        List<ColunaExport<Especialidade>> colunas = colunasEspecialidade();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Especialidades", filtrosEspecialidade(codigo, nome), colunas, dados)
                : exportacaoService.excel("Especialidades", colunas, dados);
        String nomeArquivo = "especialidades-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosEspecialidade(Long codigo, String nome) {
        String textoNome = (nome == null || nome.isBlank()) ? "Todos" : nome.trim();
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", textoNome));
    }

    private static List<ColunaExport<Especialidade>> colunasEspecialidade() {
        return List.of(
                ColunaExport.de("Código", e -> e.getId() == null ? "" : String.valueOf(e.getId())),
                ColunaExport.de("Nome", Especialidade::getNome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Especialidade> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Especialidade criar(@RequestBody Especialidade especialidade) {
        especialidade.setId(null);
        return repository.save(especialidade);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Especialidade> atualizar(@PathVariable Long id, @RequestBody Especialidade especialidade) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(especialidade.getNome());
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
