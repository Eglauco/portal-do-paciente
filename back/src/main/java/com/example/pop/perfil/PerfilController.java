package com.example.pop.perfil;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

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
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.UsuarioRepository;

import jakarta.validation.Valid;

/** CRUD dos perfis de acesso (back-office). Sob /perfil/** → ADMIN. */
@RestController
@RequestMapping("/perfil")
public class PerfilController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final PerfilRepository repository;
    private final UnidadeRepository unidadeRepository;
    private final UsuarioRepository usuarioRepository;

    public PerfilController(PerfilRepository repository, UnidadeRepository unidadeRepository,
            UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.unidadeRepository = unidadeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Lista os perfis de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<PerfilResponse> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "nome"));
        Page<Perfil> resultado = repository.search(codigo, filtroNome, pageable);

        return new Pagina<>(
                resultado.getContent().stream().map(PerfilResponse::from).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /** Catálogo de telas disponíveis (chave + rótulo) para montar o formulário. */
    @GetMapping("/telas")
    public List<TelaResponse> telas() {
        return Arrays.stream(Tela.values()).map(t -> new TelaResponse(t.name(), t.getDescricao())).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerfilResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> ResponseEntity.ok(PerfilResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerfilResponse criar(@Valid @RequestBody PerfilRequest request) {
        Perfil perfil = new Perfil();
        aplicar(perfil, request);
        perfil.setCriadoEm(LocalDateTime.now());
        return PerfilResponse.from(repository.save(perfil));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PerfilResponse> atualizar(@PathVariable Long id, @Valid @RequestBody PerfilRequest request) {
        return repository.findById(id)
                .map(perfil -> {
                    aplicar(perfil, request);
                    return ResponseEntity.ok(PerfilResponse.from(repository.save(perfil)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Bloqueia excluir um perfil em uso: como usuario_perfil tem ON DELETE CASCADE,
        // apagar removeria o vínculo e poderia deixar o usuário sem nenhum perfil (sem acesso).
        long emUso = usuarioRepository.countByPerfis_Id(id);
        if (emUso > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Perfil vinculado a " + emUso + " usuário(s). Remova o vínculo antes de excluir.");
        }
        repository.deleteById(id); // vínculos (perfil_tela, perfil_unidade) caem por CASCADE
        return ResponseEntity.noContent().build();
    }

    /** Duplica um perfil (mesmas telas + unidades) como "Cópia de {nome}" para agilizar um novo cadastro. */
    @PostMapping("/{id}/duplicar")
    public ResponseEntity<PerfilResponse> duplicar(@PathVariable Long id) {
        return repository.findById(id)
                .map(original -> {
                    Perfil copia = new Perfil();
                    copia.setNome("Cópia de " + original.getNome());
                    copia.setTelas(new HashSet<>(original.getTelas()));
                    copia.setUnidades(new HashSet<>(original.getUnidades()));
                    copia.setCriadoEm(LocalDateTime.now());
                    return ResponseEntity.status(HttpStatus.CREATED).body(PerfilResponse.from(repository.save(copia)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void aplicar(Perfil perfil, PerfilRequest request) {
        perfil.setNome(request.nome().trim());
        perfil.setTelas(request.telas() == null ? new HashSet<>() : new HashSet<>(request.telas()));
        List<Long> ids = request.unidadeIds() == null ? List.of() : request.unidadeIds();
        List<Unidade> encontradas = unidadeRepository.findAllById(ids);
        // findAllById ignora ids inexistentes silenciosamente — validamos para não gravar parcial.
        if (encontradas.size() != ids.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alguma unidade informada não existe");
        }
        perfil.setUnidades(new HashSet<>(encontradas));
    }

    public record TelaResponse(String chave, String descricao) {
    }
}
