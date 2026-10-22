package com.example.pop.credencialia;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.credencialia.ConfigIaService.ResultadoTeste;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Tela de credencial de IA (Claude/Anthropic): o cliente cadastra a API key POR UNIDADE e/ou o PADRÃO
 * do inquilino (chave + modelos por frente). A api-key é WRITE-ONLY (a API nunca devolve em claro, só
 * o flag {@code apiKeyPreenchida}). Sob /configuracao-ia (ADMIN).
 */
@RestController
@RequestMapping("/configuracao-ia")
public class ConfiguracaoIaController {

    private final ConfigIaService service;
    private final UnidadeRepository unidadeRepository;

    public ConfiguracaoIaController(ConfigIaService service, UnidadeRepository unidadeRepository) {
        this.service = service;
        this.unidadeRepository = unidadeRepository;
    }

    /** Provedores de IA oferecidos no select da credencial (suportados + com modelos ativos). */
    @GetMapping("/provedores")
    public List<TipoProvedorIa> provedores() {
        return service.provedoresDisponiveis();
    }

    // ---- Por unidade ----

    @GetMapping("/unidade/{unidadeId}")
    public StatusIa statusUnidade(@PathVariable Long unidadeId) {
        exigirUnidade(unidadeId);
        return service.status(EscopoIa.unidade(unidadeId));
    }

    @PutMapping("/unidade/{unidadeId}")
    public StatusIa salvarUnidade(@PathVariable Long unidadeId, @RequestBody FormIa form) {
        exigirUnidade(unidadeId);
        service.salvar(EscopoIa.unidade(unidadeId), form);
        return service.status(EscopoIa.unidade(unidadeId));
    }

    @PostMapping("/unidade/{unidadeId}/testar")
    public ResultadoTeste testarUnidade(@PathVariable Long unidadeId, @RequestBody(required = false) FormIa form) {
        exigirUnidade(unidadeId);
        return service.testar(EscopoIa.unidade(unidadeId), form);
    }

    /** Remove o IA próprio da unidade → ela passa a usar o padrão do inquilino. */
    @DeleteMapping("/unidade/{unidadeId}")
    public StatusIa removerUnidade(@PathVariable Long unidadeId) {
        exigirUnidade(unidadeId);
        service.remover(EscopoIa.unidade(unidadeId));
        return service.status(EscopoIa.unidade(unidadeId));
    }

    // ---- Padrão do inquilino ----

    @GetMapping("/inquilino")
    public StatusIa statusInquilino() {
        return service.status(EscopoIa.inquilinoPadrao());
    }

    @PutMapping("/inquilino")
    public StatusIa salvarInquilino(@RequestBody FormIa form) {
        service.salvar(EscopoIa.inquilinoPadrao(), form);
        return service.status(EscopoIa.inquilinoPadrao());
    }

    @PostMapping("/inquilino/testar")
    public ResultadoTeste testarInquilino(@RequestBody(required = false) FormIa form) {
        return service.testar(EscopoIa.inquilinoPadrao(), form);
    }

    // ---- helpers ----

    private void exigirUnidade(Long unidadeId) {
        if (unidadeId == null || !unidadeRepository.existsById(unidadeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade não encontrada.");
        }
    }
}
