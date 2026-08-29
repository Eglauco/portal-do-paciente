package com.example.pop.motivofalta;

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
class MotivoFaltaControllerTest {

    @Autowired
    private MotivoFaltaController controller;

    @Test
    void listaMotivosSemeados() {
        Pagina<MotivoFalta> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos os motivos semeados");
    }

    @Test
    void ativosNaoIncluemInativos() {
        List<MotivoFalta> ativos = controller.ativos();
        assertTrue(ativos.stream().allMatch(MotivoFalta::isAtivo));
        assertTrue(ativos.stream().anyMatch(m -> m.getMotivo().equals("Problema de transporte")));
        assertTrue(ativos.stream().noneMatch(m -> m.getMotivo().equals("Dificuldade financeira")));
    }

    @Test
    void criaAtualizaExclui() {
        MotivoFalta novo = controller.criar(new MotivoFaltaRequest("Motivo de teste", true));
        Long id = novo.getId();
        assertNotNull(id);

        MotivoFalta atualizado = controller
                .atualizar(id, new MotivoFaltaRequest("Motivo de teste editado", false)).getBody();
        assertNotNull(atualizado);
        assertEquals("Motivo de teste editado", atualizado.getMotivo());
        assertFalse(atualizado.isAtivo());

        controller.excluir(id);
        assertEquals(404, controller.buscar(id).getStatusCode().value());
    }
}
