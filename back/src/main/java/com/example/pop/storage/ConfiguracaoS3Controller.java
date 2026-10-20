package com.example.pop.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.storage.ConfigS3Service.ResultadoTeste;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Tela de Armazenamento (S3): o admin configura o S3 POR UNIDADE (custo/isolamento) e o S3 PADRÃO
 * do inquilino (fotos, modelos de TCLE, logomarca). A secret-key é WRITE-ONLY (a API nunca devolve
 * em claro, só o flag {@code secretPreenchido}). Sob /configuracao-s3 (ADMIN).
 */
@RestController
@RequestMapping("/configuracao-s3")
public class ConfiguracaoS3Controller {

    private final ConfigS3Service service;
    private final UnidadeRepository unidadeRepository;

    public ConfiguracaoS3Controller(ConfigS3Service service, UnidadeRepository unidadeRepository) {
        this.service = service;
        this.unidadeRepository = unidadeRepository;
    }

    // ---- Por unidade ----

    @GetMapping("/unidade/{unidadeId}")
    public StatusS3 statusUnidade(@PathVariable Long unidadeId) {
        exigirUnidade(unidadeId);
        return service.status(EscopoS3.unidade(unidadeId));
    }

    @PutMapping("/unidade/{unidadeId}")
    public StatusS3 salvarUnidade(@PathVariable Long unidadeId, @RequestBody FormS3 form) {
        exigirUnidade(unidadeId);
        service.salvar(EscopoS3.unidade(unidadeId), form);
        return service.status(EscopoS3.unidade(unidadeId));
    }

    @PostMapping("/unidade/{unidadeId}/testar")
    public ResultadoTeste testarUnidade(@PathVariable Long unidadeId, @RequestBody(required = false) FormS3 form) {
        exigirUnidade(unidadeId);
        return service.testar(EscopoS3.unidade(unidadeId), form);
    }

    // ---- Padrão do inquilino ----

    @GetMapping("/inquilino")
    public StatusS3 statusInquilino() {
        return service.status(EscopoS3.inquilinoPadrao());
    }

    @PutMapping("/inquilino")
    public StatusS3 salvarInquilino(@RequestBody FormS3 form) {
        service.salvar(EscopoS3.inquilinoPadrao(), form);
        return service.status(EscopoS3.inquilinoPadrao());
    }

    @PostMapping("/inquilino/testar")
    public ResultadoTeste testarInquilino(@RequestBody(required = false) FormS3 form) {
        return service.testar(EscopoS3.inquilinoPadrao(), form);
    }

    // ---- helpers ----

    private void exigirUnidade(Long unidadeId) {
        if (unidadeId == null || !unidadeRepository.existsById(unidadeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade não encontrada.");
        }
    }
}
