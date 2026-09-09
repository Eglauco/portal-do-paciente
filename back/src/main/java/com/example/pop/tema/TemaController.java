package com.example.pop.tema;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tema da plataforma (cor primária + tons derivados) para o front e o app. */
@RestController
public class TemaController {

    private final TemaService temaService;

    public TemaController(TemaService temaService) {
        this.temaService = temaService;
    }

    /** Paleta atual — PÚBLICA (o app do paciente não tem login; lida no boot do front/app). */
    @GetMapping("/tema")
    public PaletaTema tema() {
        return temaService.tema();
    }

    /**
     * Prévia da paleta derivada de uma cor candidata, SEM salvar — para o seletor de cor do
     * admin mostrar o resultado (com clamp/contraste) antes de confirmar. Só admin.
     */
    @GetMapping("/tema/preview")
    public PaletaTema preview(@RequestParam String cor) {
        return temaService.derivar(cor);
    }
}
