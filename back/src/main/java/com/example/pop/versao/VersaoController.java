package com.example.pop.versao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Versao (numero + data) de back, front e app. */
@RestController
public class VersaoController {

    private final VersaoService versaoService;

    public VersaoController(VersaoService versaoService) {
        this.versaoService = versaoService;
    }

    /** Versoes atuais — PUBLICA (o app e o admin leem no boot; sem autenticacao, igual /tema e /marca). */
    @GetMapping("/versao")
    public VersaoResponse versao() {
        return versaoService.versao();
    }
}
