package com.example.pop.conselho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class ConselhoControllerTest {

    @Autowired
    private ConselhoController controller;

    @Test
    void listaConselhosSemeados() {
        Pagina<Conselho> pagina = controller.listar(null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 10, "esperado ao menos os conselhos semeados");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Conselho> pagina = controller.listar(null, "Enfermagem", null, 0, 10);
        assertTrue(pagina.totalElements() >= 1, "COREN deve aparecer no filtro por nome");
        assertTrue(pagina.content().stream().allMatch(c -> c.getNome().toLowerCase().contains("enfermagem")),
                "todos os resultados batem com o filtro");
        assertTrue(pagina.content().stream().anyMatch(c -> "COREN".equals(c.getSigla())),
                "o conselho de enfermagem semeado (sigla COREN) está presente");
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Conselho> pagina = controller.listar(null, null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
