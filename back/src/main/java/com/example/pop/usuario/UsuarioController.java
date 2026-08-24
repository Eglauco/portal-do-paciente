package com.example.pop.usuario;

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

import com.example.pop.common.Pagina;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final UsuarioRepository repository;

    public UsuarioController(UsuarioRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista usuários de forma paginada, com filtros opcionais por código, nome e e-mail.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Usuario> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroEmail = (email == null) ? "" : email.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Usuario> resultado = repository.search(codigo, filtroNome, filtroEmail, pageable);

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario criar(@RequestBody Usuario usuario) {
        usuario.setId(null);
        return repository.save(usuario);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @RequestBody Usuario usuario) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(usuario.getNome());
                    existente.setEmail(usuario.getEmail());
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
