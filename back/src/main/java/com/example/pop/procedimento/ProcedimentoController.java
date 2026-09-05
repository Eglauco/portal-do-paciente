package com.example.pop.procedimento;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/procedimento")
public class ProcedimentoController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final ProcedimentoRepository repository;
    private final ExportacaoService exportacaoService;

    public ProcedimentoController(ProcedimentoRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    /**
     * Lista procedimentos de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Procedimento> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Procedimento> resultado = repository.search(codigo, filtroNome, pageable);

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
     * Exporta os procedimentos que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por código.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        List<Procedimento> dados = repository.search(codigo, filtroNome, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Procedimento::getId))
                .toList();
        List<ColunaExport<Procedimento>> cols = ExportacaoService.filtrar(colunasProcedimento(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Procedimentos", filtrosProcedimento(codigo, filtroNome), cols, dados)
                : exportacaoService.excel("Procedimentos", cols, dados);
        String arquivoNome = "procedimentos-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivoNome + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasProcedimento().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosProcedimento(Long codigo, String nome) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", nome != null && !nome.isBlank() ? nome : "Todos"));
    }

    /** Todas as colunas disponíveis do procedimento (o usuário escolhe quais exportar). */
    private static List<ColunaExport<Procedimento>> colunasProcedimento() {
        return List.of(
                ColunaExport.de("Código", p -> p.getId() == null ? "" : String.valueOf(p.getId())),
                ColunaExport.de("Nome", p -> p.getNome() == null ? "" : p.getNome()),
                ColunaExport.de("Preparo", p -> p.getPreparo() == null ? "" : p.getPreparo()),
                ColunaExport.de("Horas para cancelamento",
                        p -> p.getHorasCancelamento() == null ? "" : String.valueOf(p.getHorasCancelamento())),
                ColunaExport.de("Horas para NPS",
                        p -> p.getHorasNps() == null ? "" : String.valueOf(p.getHorasNps())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Procedimento> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Procedimento criar(@Valid @RequestBody Procedimento procedimento) {
        procedimento.setId(null);
        return repository.save(procedimento);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Procedimento> atualizar(@PathVariable Long id,
            @Valid @RequestBody Procedimento procedimento) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(procedimento.getNome());
                    existente.setPreparo(procedimento.getPreparo());
                    existente.setHorasCancelamento(procedimento.getHorasCancelamento());
                    existente.setHorasNps(procedimento.getHorasNps());
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
