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
    void confirmarECancelarAlteramOStatus() {
        AgendamentoRequest request = new AgendamentoRequest(
                LocalDateTime.of(2026, 10, 2, 9, 0), 1L, 1L, 1L, 1L, 1L, null);
        AgendamentoResponse criado = controller.criar(request);
        assertEquals(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, criado.statusAgendamento());

        AgendamentoResponse confirmado = controller.confirmar(criado.id()).getBody();
        assertEquals(StatusAgendamento.PACIENTE_CONFIRMOU, confirmado.statusAgendamento());

        AgendamentoResponse cancelado = controller.cancelar(criado.id()).getBody();
        assertEquals(StatusAgendamento.CANCELADO_PELO_PACIENTE, cancelado.statusAgendamento());

        controller.excluir(criado.id());
    }

    @Test
    void novoAgendamentoNasceAguardandoConfirmacao() {
        AgendamentoRequest request = new AgendamentoRequest(
                LocalDateTime.of(2026, 10, 1, 9, 0),
                1L, // especialidadeId
                1L, // profissionalSaudeId
                1L, // procedimentoId
                1L, // pacienteId
                1L, // unidadeSaudeId
                StatusAgendamento.PRESENCA_PACIENTE); // deve ser ignorado na criação
        AgendamentoResponse criado = controller.criar(request);
        assertEquals(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, criado.statusAgendamento());
        // Limpa o registro criado no teste.
        controller.excluir(criado.id());
    }
}
