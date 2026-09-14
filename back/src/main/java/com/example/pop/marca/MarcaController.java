package com.example.pop.marca;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Textos de marca da plataforma (white-label) para o front pintar o login. */
@RestController
public class MarcaController {

    private final MarcaService marcaService;

    public MarcaController(MarcaService marcaService) {
        this.marcaService = marcaService;
    }

    /** Marca atual — PÚBLICA (a tela de login é pré-autenticação; lida no boot do front). */
    @GetMapping("/marca")
    public MarcaResponse marca() {
        return marcaService.marca();
    }
}
