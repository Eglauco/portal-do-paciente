package com.example.pop.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.unidade.UnidadeRepository;

@SpringBootTest
class DashboardControllerTest {

    @Autowired
    private DashboardController controller;
    @Autowired
    private UnidadeRepository unidadeRepository;

    private Long unidade() {
        return unidadeRepository.findAll().get(0).getId();
    }

    @Test
    void geralRetornaMetricasCoerentes() {
        GeralDashboard d = controller.geral(unidade(), 30);
        assertNotNull(d);
        assertEquals(30, d.dias());
        assertTrue(d.agendamentosPorStatus().size() == 6, "6 status de agendamento");
        assertFalse(d.agendamentosPorDia().isEmpty(), "série diária preenchida");
        assertTrue(d.taxaComparecimento() >= 0 && d.taxaComparecimento() <= 100);
        assertTrue(d.pacientesTotal() >= 0);
    }

    @Test
    void agendamentosRetornaDistribuicoes() {
        AgendamentoDashboard d = controller.agendamentos(unidade(), 30);
        assertNotNull(d);
        assertEquals(6, d.porStatus().size());
        assertNotNull(d.topProcedimentos());
        assertNotNull(d.topProfissionais());
        assertNotNull(d.porEspecialidade());
        assertNotNull(d.motivosFalta());
        // total = soma das fatias
        long soma = d.porStatus().stream().mapToLong(Fatia::valor).sum();
        assertEquals(d.total(), soma);
    }

    @Test
    void chatsRetornaSnapshotEVolume() {
        ChatDashboard d = controller.chats(unidade(), 30);
        assertNotNull(d);
        assertEquals(4, d.porStatus().size());
        assertEquals(d.mensagensPaciente() + d.mensagensUnidade(), d.mensagensPeriodo());
        assertNotNull(d.cargaPorAtendente());
        assertTrue(d.taxaResolucao() >= 0 && d.taxaResolucao() <= 100);
    }

    @Test
    void sauRetornaStatusETipos() {
        SauDashboard d = controller.sau(unidade(), 30);
        assertNotNull(d);
        assertEquals(3, d.porStatus().size());
        assertNotNull(d.porTipo());
        assertNotNull(d.cargaPorAtendente());
        assertEquals(d.abertas(), d.aguardandoSau() + d.aguardandoPaciente());
    }

    @Test
    void npsRetornaMediasEDistribuicao() {
        NpsDashboard d = controller.nps(unidade(), 30);
        assertNotNull(d);
        assertEquals(3, d.porStatus().size());
        assertEquals(5, d.distribuicaoNotas().size(), "1..5 estrelas");
        assertNotNull(d.mediaPorCategoria());
        assertTrue(d.taxaResposta() >= 0 && d.taxaResposta() <= 100);
    }

    @Test
    void diasForaDaFaixaSaoNormalizados() {
        assertEquals(1, controller.geral(unidade(), 0).dias());
        assertEquals(365, controller.geral(unidade(), 99999).dias());
    }
}
