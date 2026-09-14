package com.example.pop.especialidade;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
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
@RequestMapping("/especialidade")
public class EspecialidadeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

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
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
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
        List<Especialidade> dados = repository.search(codigo, filtroNome,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<Especialidade>> cols = ExportacaoService.filtrar(colunasEspecialidade(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Especialidades", filtrosEspecialidade(codigo, nome), cols, dados)
                : exportacaoService.excel("Especialidades", cols, dados);
        String nomeArquivo = "especialidades-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasEspecialidade().stream().map(ColunaExport::titulo).toList();
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
                ColunaExport.de("Nome", Especialidade::getNome),
                ColunaExport.de("Cód. integração", e -> e.getCodigoIntegracao() == null ? "" : e.getCodigoIntegracao()));
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
        especialidade.setNome(validarNome(especialidade.getNome()));
        especialidade.setCodigoIntegracao(limpar(especialidade.getCodigoIntegracao()));
        validarUnicidade(especialidade.getCodigoIntegracao(), -1L);
        return salvarUnico(especialidade);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Especialidade> atualizar(@PathVariable Long id, @RequestBody Especialidade especialidade) {
        return repository.findById(id)
                .map(existente -> {
                    String codigo = limpar(especialidade.getCodigoIntegracao());
                    validarUnicidade(codigo, id);
                    existente.setNome(validarNome(especialidade.getNome()));
                    existente.setCodigoIntegracao(codigo);
                    return ResponseEntity.ok(salvarUnico(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Nome é obrigatório (máx. 120) — validado ANTES do saveAndFlush para que a violação de
     * NOT NULL/tamanho vire 422, e não aflore como o 409 de código duplicado do salvarUnico.
     */
    private static String validarNome(String valor) {
        String nome = (valor == null) ? "" : valor.trim();
        if (nome.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Informe o nome da especialidade.");
        }
        if (nome.length() > 120) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nome deve ter no máximo 120 caracteres.");
        }
        return nome;
    }

    /** Trim + null quando vazio (evita gravar "" colidindo no índice único parcial). */
    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > 60) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Código de integração deve ter no máximo 60 caracteres.");
        }
        return limpo;
    }

    /** Código de integração é único quando preenchido (ignora o próprio registro na edição). */
    private void validarUnicidade(String codigoIntegracao, Long id) {
        if (codigoIntegracao != null && repository.existsByCodigoIntegracaoAndIdNot(codigoIntegracao, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma especialidade com este código de integração.");
        }
    }

    /** Salva com flush para o índice único disparar aqui; traduz a corrida para 409. */
    private Especialidade salvarUnico(Especialidade especialidade) {
        try {
            return repository.saveAndFlush(especialidade);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma especialidade com este código de integração.");
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
