package com.example.pop.agendamento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.pop.push.PushService;

@SpringBootTest
class AgendamentoFaltaNotificacaoTest {

    @Autowired
    private AgendamentoController controller;
    @Autowired
    private AgendamentoRepository repository;

    @MockitoBean
    private PushService pushService;

    @Test
    void marcarFaltaNotificaOPacienteUmaUnicaVez() {
        LocalDateTime dh = LocalDateTime.of(2026, 12, 1, 9, 0);
        AgendamentoResponse criado = controller.criar(new AgendamentoRequest(dh, 1L, 1L, 1L, 1L, 1L, null));

        // Transição AGUARDANDO_CONFIRMACAO -> FALTA_PACIENTE: notifica.
        controller.atualizar(criado.id(),
                new AgendamentoRequest(dh, 1L, 1L, 1L, 1L, 1L, StatusAgendamento.FALTA_PACIENTE));
        verify(pushService, times(1)).notificarFaltaPaciente(any());

        // Salvar de novo já em FALTA (sem transição): não notifica outra vez.
        controller.atualizar(criado.id(),
                new AgendamentoRequest(dh, 1L, 1L, 1L, 1L, 1L, StatusAgendamento.FALTA_PACIENTE));
        verify(pushService, times(1)).notificarFaltaPaciente(any());

        repository.deleteById(criado.id());
    }
}
