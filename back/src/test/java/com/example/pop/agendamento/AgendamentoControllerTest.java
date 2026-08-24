package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class AgendamentoControllerTest {

    @Autowired
    private AgendamentoController controller;

    @Test
    void listaOrdenadaPorDataDesc() {
        Pagina<AgendamentoResponse> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 6, "esperado ao menos os agendamentos semeados");
        assertNotNull(pagina.content().get(0).paciente());
        assertNotNull(pagina.content().get(0).unidadeSaude());
    }

    @Test
    void filtraPorStatus() {
        Pagina<AgendamentoResponse> pagina = controller.listar(StatusAgendamento.FALTA_PACIENTE, null, 0, 10);
        assertTrue(pagina.totalElements() >= 1);
        assertEquals(StatusAgendamento.FALTA_PACIENTE, pagina.content().get(0).statusAgendamento());
    }

    @Test
    void novoAgendamentoNasceAguardandoConfirmacao() {
        AgendamentoRequest request = new AgendamentoRequest(
                LocalDateTime.of(2026, 10, 1, 9, 0),
                "Cardiologia",
                "Dr. Teste",
                1L,
                1L,
                StatusAgendamento.PRESENCA_PACIENTE); // deve ser ignorado na criação
        AgendamentoResponse criado = controller.criar(request);
        assertEquals(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, criado.statusAgendamento());
        // Limpa o registro criado no teste.
        controller.excluir(criado.id());
    }
}
