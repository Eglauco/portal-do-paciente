package com.example.pop.tema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Integração: a config semeada (V77) flui até o endpoint /tema com a paleta derivada. */
@SpringBootTest
class TemaControllerTest {

    @Autowired
    private TemaController controller;

    @Test
    void temaDevolveAPaletaDaCorSemeada() {
        PaletaTema p = controller.tema();
        // V77 semeia COR_PRIMARIA_PLATAFORMA = #0E8C7F (o verde atual).
        assertEquals("#0E8C7F", p.brand());
        assertEquals("#FFFFFF", p.onBrand());
        assertTrue(p.brandDeep().matches("^#[0-9A-F]{6}$"));
        assertTrue(p.glow().matches("^#[0-9A-F]{6}$"));
    }

    @Test
    void previewDerivaSemSalvar() {
        assertEquals("#0E8C7F", controller.tema().brand(), "preview não deve alterar o valor salvo");
        PaletaTema azul = controller.preview("#3F8CFF");
        assertTrue(azul.brand().matches("^#[0-9A-F]{6}$"));
        assertEquals("#0E8C7F", controller.tema().brand(), "preview não persiste");
    }
}
