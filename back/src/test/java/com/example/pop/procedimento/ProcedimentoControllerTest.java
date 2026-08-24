package com.example.pop.procedimento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class ProcedimentoControllerTest {

    @Autowired
    private ProcedimentoController controller;

    @Test
    void listaProcedimentosSemeadas() {
        Pagina<Procedimento> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 6, "esperado ao menos as procedimentos semeadas");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Procedimento> pagina = controller.listar(null, "raio", 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Exame de raio-x", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Procedimento> pagina = controller.listar(null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
