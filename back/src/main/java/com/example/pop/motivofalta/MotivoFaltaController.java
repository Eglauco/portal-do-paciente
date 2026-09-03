package com.example.pop.motivofalta;

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
@RequestMapping("/motivo-falta")
public class MotivoFaltaController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final MotivoFaltaRepository repository;
    private final ExportacaoService exportacaoService;

    public MotivoFaltaController(MotivoFaltaRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<MotivoFalta> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String motivo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroMotivo = (motivo == null) ? "" : motivo.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<MotivoFalta> resultado = repository.search(codigo, filtroMotivo, pageable);

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
     * Exporta os motivos de falta que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por código.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String motivo) {
        String filtroMotivo = (motivo == null) ? "" : motivo.trim();
        List<MotivoFalta> dados = repository.search(codigo, filtroMotivo, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(MotivoFalta::getId))
                .toList();
        List<ColunaExport<MotivoFalta>> colunas = colunasMotivoFalta();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Motivos de falta", filtrosMotivoFalta(codigo, filtroMotivo), colunas, dados)
                : exportacaoService.excel("Motivos de falta", colunas, dados);
        String nome = "motivos-falta-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosMotivoFalta(Long codigo, String motivo) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Motivo", motivo != null && !motivo.isBlank() ? motivo : "Todos"));
    }

    private static List<ColunaExport<MotivoFalta>> colunasMotivoFalta() {
        return List.of(
                ColunaExport.de("Código", m -> String.valueOf(m.getId())),
                ColunaExport.de("Motivo", MotivoFalta::getMotivo),
                ColunaExport.de("Situação", m -> m.isAtivo() ? "Ativo" : "Inativo"));
    }

    /** Motivos ativos (usado pelo app para o paciente selecionar ao justificar a falta). */
    @GetMapping("/ativos")
    public List<MotivoFalta> ativos() {
        return repository.findByAtivoTrueOrderByMotivo();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MotivoFalta> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MotivoFalta criar(@Valid @RequestBody MotivoFaltaRequest dados) {
        MotivoFalta motivo = new MotivoFalta();
        motivo.setMotivo(dados.motivo());
        motivo.setAtivo(dados.ativo() == null || dados.ativo());
        return repository.save(motivo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MotivoFalta> atualizar(@PathVariable Long id, @Valid @RequestBody MotivoFaltaRequest dados) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setMotivo(dados.motivo());
                    if (dados.ativo() != null) {
                        existente.setAtivo(dados.ativo());
                    }
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
