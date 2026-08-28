package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;

@SpringBootTest
class AgendamentoControllerTest {

    @Autowired
    private AgendamentoController controller;

    @Test
    void listaOrdenadaPorDataDesc() {
        Pagina<AgendamentoResponse> pagina = controller.listar(null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 6, "esperado ao menos os agendamentos semeados");
        assertNotNull(pagina.content().get(0).paciente());
        assertNotNull(pagina.content().get(0).unidadeSaude());
    }

    @Test
    void filtraPorStatus() {
        Pagina<AgendamentoResponse> pagina = controller.listar(StatusAgendamento.FALTA_PACIENTE, null, null, 0, 10);
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
    void justificarFaltaRegistraMotivosETexto() {
        AgendamentoResponse criado = controller.criar(new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 3, 8, 0), 1L, 1L, 1L, 1L, 1L, null));
        Long id = criado.id();
        // Admin marca a falta do paciente.
        controller.atualizar(id, new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 3, 8, 0), 1L, 1L, 1L, 1L, 1L, StatusAgendamento.FALTA_PACIENTE));

        // Paciente justifica a falta (motivos semeados 1 e 2 + texto).
        AgendamentoResponse just = controller.justificarFalta(id,
                new JustificarFaltaRequest(List.of(1L, 2L), "Não consegui ir por causa do transporte")).getBody();
        assertNotNull(just);
        assertTrue(just.faltaJustificada());
        assertEquals(2, just.motivosFalta().size());
        assertEquals("Não consegui ir por causa do transporte", just.justificativaFalta());

        controller.excluir(id);
    }

    @Test
    void justificarFaltaExigeStatusFalta() {
        AgendamentoResponse criado = controller.criar(new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 4, 8, 0), 1L, 1L, 1L, 1L, 1L, null));
        Long id = criado.id();
        // Status AGUARDANDO -> justificar deve ser recusado.
        assertThrows(ResponseStatusException.class,
                () -> controller.justificarFalta(id, new JustificarFaltaRequest(List.of(1L), null)));
        controller.excluir(id);
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
