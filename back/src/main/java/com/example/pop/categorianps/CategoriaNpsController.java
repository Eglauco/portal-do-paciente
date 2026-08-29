package com.example.pop.categorianps;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
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

import com.example.pop.common.Pagina;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/categoria-nps")
public class CategoriaNpsController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final CategoriaNpsRepository repository;

    public CategoriaNpsController(CategoriaNpsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Pagina<CategoriaNps> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<CategoriaNps> resultado = repository.search(codigo, filtroNome, pageable);

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /** Categorias ativas (usado pelo app para o paciente avaliar). */
    @GetMapping("/ativos")
    public List<CategoriaNps> ativos() {
        return repository.findByAtivoTrueOrderByNome();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaNps> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaNps criar(@Valid @RequestBody CategoriaNpsRequest dados) {
        CategoriaNps categoria = new CategoriaNps();
        categoria.setNome(dados.nome());
        categoria.setAtivo(dados.ativo() == null || dados.ativo());
        return repository.save(categoria);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaNps> atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaNpsRequest dados) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(dados.nome());
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
        try {
            repository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            // Categoria já usada em avaliações (FK em nps_categoria_nota): oriente a desativar.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Categoria já utilizada em avaliações; desative-a em vez de excluir");
        }
        return ResponseEntity.noContent().build();
    }
}
