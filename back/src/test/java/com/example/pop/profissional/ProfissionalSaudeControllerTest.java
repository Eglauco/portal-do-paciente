package com.example.pop.profissional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class ProfissionalSaudeControllerTest {

    @Autowired
    private ProfissionalSaudeController controller;

    @Test
    void listaProfissionalSaudesSemeadas() {
        Pagina<ProfissionalSaude> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 7, "esperado ao menos as profissionals semeadas");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<ProfissionalSaude> pagina = controller.listar(null, "Helena", 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Dra. Helena Costa", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<ProfissionalSaude> pagina = controller.listar(null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
