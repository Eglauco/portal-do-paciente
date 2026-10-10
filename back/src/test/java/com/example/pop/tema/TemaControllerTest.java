package com.example.pop.tema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integração: a config de cor flui até o endpoint {@code /tema} com a paleta DERIVADA. Roda sem
 * inquilino resolvido (pré-login), então o {@code /tema} serve a cor da PLATAFORMA — que o super-admin
 * edita no banco de dev — por isso NÃO se asserta um hex exato, só o contrato: a paleta é bem-formada
 * (todos os tons são {@code #RRGGBB} válidos, derivados no backend) e o {@code preview} NÃO persiste.
 */
@SpringBootTest
class TemaControllerTest {

    @Autowired
    private TemaController controller;

    @Test
    void temaDevolveUmaPaletaBemFormada() {
        PaletaTema p = controller.tema();
        assertTrue(p.brand().matches("^#[0-9A-F]{6}$"), "brand deve ser #RRGGBB");
        assertTrue(p.brandDeep().matches("^#[0-9A-F]{6}$"), "brandDeep deve ser #RRGGBB");
        assertTrue(p.brandPine().matches("^#[0-9A-F]{6}$"), "brandPine deve ser #RRGGBB");
        assertTrue(p.glow().matches("^#[0-9A-F]{6}$"), "glow deve ser #RRGGBB");
        assertTrue(p.bg().matches("^#[0-9A-F]{6}$"), "bg deve ser #RRGGBB");
        assertTrue(p.brandRgb().matches("^\\d{1,3}, \\d{1,3}, \\d{1,3}$"), "brandRgb no formato \"r, g, b\"");
        // onBrand é branco OU o escuro de marca, conforme o contraste calculado na derivação.
        assertTrue(p.onBrand().equals("#FFFFFF") || p.onBrand().equals("#0C1F1C"), "onBrand branco ou escuro");
    }

    @Test
    void previewNaoPersiste() {
        String antes = controller.tema().brand();
        PaletaTema azul = controller.preview("#3F8CFF");
        assertTrue(azul.brand().matches("^#[0-9A-F]{6}$"), "preview deriva uma paleta válida");
        assertEquals(antes, controller.tema().brand(), "preview NÃO altera o valor servido pelo /tema");
    }
}
