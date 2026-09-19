package com.example.pop.prontuario;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import com.example.pop.common.Ref;

import jakarta.validation.Valid;

/** Cadastro de Tipos de Documento do Prontuário (back-office / ADMIN). */
@RestController
@RequestMapping("/tipo-documento-prontuario")
public class TipoDocumentoProntuarioController {

    private static final int TAMANHO_MAXIMO = 100;

    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome",
            "ativo", "ativo");
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    private final TipoDocumentoProntuarioRepository repository;
    private final DocumentoRepository documentoRepository;

    public TipoDocumentoProntuarioController(TipoDocumentoProntuarioRepository repository,
            DocumentoRepository documentoRepository) {
        this.repository = repository;
        this.documentoRepository = documentoRepository;
    }

    @GetMapping
    public Pagina<TipoDocumentoProntuarioResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<TipoDocumentoProntuario> resultado = repository.search(nome == null ? "" : nome.trim(), ativo, pageable);
        List<TipoDocumentoProntuarioResponse> content = resultado.getContent().stream()
                .map(TipoDocumentoProntuarioResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Tipos ativos (id + nome) para o seletor no upload do documento. */
    @GetMapping("/ativos")
    public List<Ref> ativos() {
        return repository.findByAtivoTrueOrderByNomeAsc().stream()
                .map(t -> new Ref(t.getId(), t.getNome())).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoDocumentoProntuarioResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(TipoDocumentoProntuarioResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoDocumentoProntuarioResponse criar(@Valid @RequestBody TipoDocumentoProntuarioRequest req) {
        if (repository.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um tipo com este nome.");
        }
        TipoDocumentoProntuario t = new TipoDocumentoProntuario();
        aplicar(t, req);
        return TipoDocumentoProntuarioResponse.from(repository.save(t));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoDocumentoProntuarioResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody TipoDocumentoProntuarioRequest req) {
        return repository.findById(id)
                .map(t -> {
                    if (repository.existsByNomeIgnoreCaseAndIdNot(req.nome().trim(), id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um tipo com este nome.");
                    }
                    aplicar(t, req);
                    return ResponseEntity.ok(TipoDocumentoProntuarioResponse.from(repository.save(t)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (documentoRepository.existsByTipo_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este tipo está em uso por documentos. Desative-o em vez de excluir.");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static void aplicar(TipoDocumentoProntuario t, TipoDocumentoProntuarioRequest req) {
        t.setNome(req.nome().trim());
        t.setPromptResumo(vazioParaNulo(req.promptResumo()));
        t.setPromptValidacao(vazioParaNulo(req.promptValidacao()));
        t.setAtivo(req.ativo() == null || req.ativo());
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
