package com.example.pop.configuracaoagenda;

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

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/configuracao-agenda")
public class ConfiguracaoAgendaController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    private final ConfiguracaoAgendaRepository repository;
    private final ExportacaoService exportacaoService;

    public ConfiguracaoAgendaController(ConfiguracaoAgendaRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    /**
     * Lista configuracaoAgendas de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<ConfiguracaoAgenda> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<ConfiguracaoAgenda> resultado = repository.search(codigo, filtroNome, pageable);

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
     * Exporta os configuracaoAgendas que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por código.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        List<ConfiguracaoAgenda> dados = repository
                .search(codigo, filtroNome, Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO)))
                .getContent();
        List<ColunaExport<ConfiguracaoAgenda>> cols = ExportacaoService.filtrar(colunasConfiguracaoAgenda(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Configuração da Agenda", filtrosConfiguracaoAgenda(codigo, filtroNome), cols, dados)
                : exportacaoService.excel("Configuração da Agenda", cols, dados);
        String arquivoNome = "configuracao-agenda-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivoNome + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasConfiguracaoAgenda().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosConfiguracaoAgenda(Long codigo, String nome) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", nome != null && !nome.isBlank() ? nome : "Todos"));
    }

    /** Todas as colunas disponíveis do configuracaoAgenda (o usuário escolhe quais exportar). */
    private static List<ColunaExport<ConfiguracaoAgenda>> colunasConfiguracaoAgenda() {
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
    public ResponseEntity<ConfiguracaoAgenda> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfiguracaoAgenda criar(@Valid @RequestBody ConfiguracaoAgenda configuracaoAgenda) {
        configuracaoAgenda.setId(null);
        return repository.save(configuracaoAgenda);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracaoAgenda> atualizar(@PathVariable Long id,
            @Valid @RequestBody ConfiguracaoAgenda configuracaoAgenda) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(configuracaoAgenda.getNome());
                    existente.setPreparo(configuracaoAgenda.getPreparo());
                    existente.setHorasCancelamento(configuracaoAgenda.getHorasCancelamento());
                    existente.setHorasNps(configuracaoAgenda.getHorasNps());
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
