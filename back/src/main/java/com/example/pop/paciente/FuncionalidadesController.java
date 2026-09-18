package com.example.pop.paciente;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kill-switch global das telas do app (habilitação por {@link FuncionalidadeApp}). */
@RestController
public class FuncionalidadesController {

    private final TelasAppService telasAppService;

    public FuncionalidadesController(TelasAppService telasAppService) {
        this.telasAppService = telasAppService;
    }

    /**
     * Mapa tela → habilitada globalmente — PÚBLICA (o app do paciente lê no boot para esconder
     * abas desligadas, igual /tema e /marca; o front lê para esconder da matriz de liberação).
     */
    @GetMapping("/funcionalidades")
    public FuncionalidadesResponse funcionalidades() {
        return new FuncionalidadesResponse(telasAppService.todas());
    }

    /** Resposta do kill-switch: mapa tela→habilitada (chave = nome do enum). */
    public record FuncionalidadesResponse(Map<FuncionalidadeApp, Boolean> telas) {
    }
}
