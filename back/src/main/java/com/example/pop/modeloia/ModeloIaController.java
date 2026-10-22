package com.example.pop.modeloia;

import java.util.List;

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

import com.example.pop.credencialia.TipoProvedorIa;

import jakarta.validation.Valid;

/**
 * Cadastro dos Modelos de IA (Claude) + preço por modelo. Fonte do select de modelos no cadastro da
 * credencial e do cálculo de custo. Sob /modelo-ia (ADMIN). {@code /ativos} lista os ativos (o select).
 */
@RestController
@RequestMapping("/modelo-ia")
public class ModeloIaController {

    private final ModeloIaRepository repository;

    public ModeloIaController(ModeloIaRepository repository) {
        this.repository = repository;
    }

    /** Todos os modelos (cadastro), em ordem de nome. */
    @GetMapping
    public List<ModeloIa> listar() {
        return repository.findAll(org.springframework.data.domain.Sort.by("nome"));
    }

    /** Só os ativos — alimenta os selects de modelo da credencial. {@code provedor} opcional filtra pelo provedor. */
    @GetMapping("/ativos")
    public List<ModeloIa> ativos(@RequestParam(required = false) TipoProvedorIa provedor) {
        return provedor == null
                ? repository.findByAtivoTrueOrderByNomeAsc()
                : repository.findByAtivoTrueAndProvedorOrderByNomeAsc(provedor);
    }

    /** Todos os provedores de IA conhecidos (alimenta o select de provedor no cadastro de modelo). */
    @GetMapping("/provedores")
    public TipoProvedorIa[] provedores() {
        return TipoProvedorIa.values();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModeloIa> buscar(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloIa criar(@Valid @RequestBody ModeloIa modelo) {
        modelo.setId(null);
        if (modelo.getProvedor() == null) {
            modelo.setProvedor(TipoProvedorIa.ANTHROPIC);
        }
        // Normaliza igual ao atualizar(): o id é buscado SEMPRE trimado (CustoIaService/ConfigIaService),
        // então gravar com espaço nas pontas quebraria o match por id exato.
        modelo.setModeloId(modelo.getModeloId().trim());
        modelo.setNome(modelo.getNome().trim());
        exigirModeloIdUnico(modelo);
        return repository.save(modelo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModeloIa> atualizar(@PathVariable Long id, @Valid @RequestBody ModeloIa dados) {
        return repository.findById(id)
                .map(existente -> {
                    dados.setId(id);
                    exigirModeloIdUnico(dados);
                    existente.setProvedor(dados.getProvedor() == null ? TipoProvedorIa.ANTHROPIC : dados.getProvedor());
                    existente.setModeloId(dados.getModeloId().trim());
                    existente.setNome(dados.getNome().trim());
                    existente.setPrecoEntradaUsdMtok(dados.getPrecoEntradaUsdMtok());
                    existente.setPrecoSaidaUsdMtok(dados.getPrecoSaidaUsdMtok());
                    existente.setAtivo(dados.isAtivo());
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

    private void exigirModeloIdUnico(ModeloIa modelo) {
        String idModelo = modelo.getModeloId() == null ? "" : modelo.getModeloId().trim();
        if (repository.existsByModeloIdAndIdNot(idModelo, modelo.getId() == null ? -1L : modelo.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um modelo com esse id (" + idModelo + ").");
        }
    }
}
