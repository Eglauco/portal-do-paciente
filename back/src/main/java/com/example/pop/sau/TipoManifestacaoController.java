package com.example.pop.sau;

import java.time.LocalDateTime;
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

import com.example.pop.common.Pagina;

import jakarta.validation.Valid;

/** Cadastro (CRUD) dos tipos de manifestação do SAU — back-office (admin). */
@RestController
@RequestMapping("/tipo-manifestacao")
public class TipoManifestacaoController {

    private static final int TAMANHO_MAXIMO = 100;

    private final TipoManifestacaoRepository repository;
    private final ManifestacaoRepository manifestacaoRepository;

    public TipoManifestacaoController(TipoManifestacaoRepository repository,
            ManifestacaoRepository manifestacaoRepository) {
        this.repository = repository;
        this.manifestacaoRepository = manifestacaoRepository;
    }

    @GetMapping
    public Pagina<TipoManifestacaoResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Sort.by(Sort.Direction.ASC, "nome"));
        Page<TipoManifestacao> resultado = repository.search(nome == null ? "" : nome.trim(), ativo, pageable);
        List<TipoManifestacaoResponse> content = resultado.getContent().stream()
                .map(TipoManifestacaoResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoManifestacaoResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(t -> ResponseEntity.ok(TipoManifestacaoResponse.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipoManifestacaoResponse criar(@Valid @RequestBody TipoManifestacaoRequest dados) {
        LocalDateTime agora = LocalDateTime.now();
        TipoManifestacao tipo = new TipoManifestacao();
        tipo.setNome(dados.nome().trim());
        tipo.setDescricao(descricaoLimpa(dados.descricao()));
        tipo.setAtivo(dados.ativo() == null || dados.ativo());
        tipo.setCriadoEm(agora);
        tipo.setAtualizadoEm(agora);
        return TipoManifestacaoResponse.from(repository.save(tipo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoManifestacaoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody TipoManifestacaoRequest dados) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(dados.nome().trim());
                    existente.setDescricao(descricaoLimpa(dados.descricao()));
                    if (dados.ativo() != null) {
                        existente.setAtivo(dados.ativo());
                    }
                    existente.setAtualizadoEm(LocalDateTime.now());
                    return ResponseEntity.ok(TipoManifestacaoResponse.from(repository.save(existente)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Exclui um tipo. Se já houver manifestações usando-o, bloqueia (desative em vez de excluir). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (manifestacaoRepository.existsByTipoId(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String descricaoLimpa(String descricao) {
        if (descricao == null) {
            return null;
        }
        String limpa = descricao.trim();
        return limpa.isEmpty() ? null : limpa;
    }
}
