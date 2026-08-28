package com.example.pop.categorianps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class CategoriaNpsControllerTest {

    @Autowired
    private CategoriaNpsController controller;

    @Test
    void listaCategoriasSemeadas() {
        Pagina<CategoriaNps> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 5, "esperado ao menos as categorias semeadas");
    }

    @Test
    void ativosNaoIncluemInativas() {
        List<CategoriaNps> ativos = controller.ativos();
        assertTrue(ativos.stream().allMatch(CategoriaNps::isAtivo));
        assertTrue(ativos.stream().anyMatch(c -> c.getNome().equals("Limpeza")));
        assertTrue(ativos.stream().noneMatch(c -> c.getNome().equals("Estacionamento")));
    }

    @Test
    void criaAtualizaExclui() {
        CategoriaNps novo = controller.criar(new CategoriaNps(null, "Categoria de teste", true));
        Long id = novo.getId();
        assertNotNull(id);

        CategoriaNps atualizado = controller
                .atualizar(id, new CategoriaNps(null, "Categoria de teste editada", false)).getBody();
        assertNotNull(atualizado);
        assertEquals("Categoria de teste editada", atualizado.getNome());
        assertFalse(atualizado.isAtivo());

        controller.excluir(id);
        assertEquals(404, controller.buscar(id).getStatusCode().value());
    }
}
