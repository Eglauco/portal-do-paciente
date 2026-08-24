package com.example.pop.unidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class UnidadeControllerTest {

    @Autowired
    private UnidadeController controller;

    @Test
    void listaUnidadesSemeadas() {
        Pagina<Unidade> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos as unidades semeadas");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Unidade> pagina = controller.listar(null, "Policl", 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Policlínica Central", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Unidade> pagina = controller.listar(null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
