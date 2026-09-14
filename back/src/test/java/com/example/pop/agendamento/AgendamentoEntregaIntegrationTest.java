package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.pop.especialidade.Especialidade;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.push.PushService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Garante que a entrega é realmente GRAVADA no banco — tanto no disparo (resumo) quanto no job
 * de receipts (promoção a ENTREGUE). O teste unitário não pega isso porque o bug era de JPA
 * (entidade destacada + open-in-view desligado): aqui a persistência é real.
 */
@SpringBootTest
class AgendamentoEntregaIntegrationTest {

    @Autowired private AgendamentoEntregaService entregaService;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private AgendamentoEntregaRepository entregaRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private ProcedimentoRepository procedimentoRepository;
    @Autowired private ProfissionalSaudeRepository profissionalRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private PacienteController pacienteController;
    @Autowired private PacienteRepository pacienteRepository;
    @MockitoBean private PushService pushService; // evita HTTP real à Expo

    private Long agendamentoId;
    private Long pacienteId;
    private Long espId;
    private Long profId;
    private Long procId;
    private Long uniId;

    @AfterEach
    void limpar() {
        if (agendamentoId != null) {
            agendamentoRepository.deleteById(agendamentoId); // cascade apaga agendamento_entrega
        }
        if (pacienteId != null) {
            pacienteRepository.deleteById(pacienteId);
        }
        if (espId != null) {
            especialidadeRepository.deleteById(espId);
        }
        if (profId != null) {
            profissionalRepository.deleteById(profId);
        }
        if (procId != null) {
            procedimentoRepository.deleteById(procId);
        }
        if (uniId != null) {
            unidadeRepository.deleteById(uniId);
        }
    }

    @Test
    void resumoDeEntregaEhPersistidoNoAgendamento() {
        Agendamento salvo = novoAgendamentoSalvo();

        entregaService.notificarNovoAgendamento(salvo);

        // Paciente sem telefone → sem aparelho → PACIENTE_SEM_APLICATIVO (e nem chama a Expo).
        EstadoEntrega resumo = agendamentoRepository.findById(agendamentoId).orElseThrow().getEntregaResumo();
        assertEquals(EstadoEntrega.PACIENTE_SEM_APLICATIVO, resumo, "resumo de entrega persistido no banco");

        List<AgendamentoEntrega> linhas = entregaRepository.findByAgendamento_IdOrderByIdAsc(agendamentoId);
        assertEquals(1, linhas.size(), "uma linha por destinatário (só o paciente)");
        assertEquals(TipoDestinatario.PACIENTE, linhas.get(0).getTipo());
        assertEquals(EstadoEntrega.PACIENTE_SEM_APLICATIVO, linhas.get(0).getEstado());
    }

    @Test
    void jobDeReceiptsPromoveParaEntregueEPersiste() {
        Agendamento salvo = novoAgendamentoSalvo();
        // Simula uma entrega "enviada" aguardando confirmação, pronta para o job (>15min atrás).
        AgendamentoEntrega entrega = new AgendamentoEntrega();
        entrega.setAgendamento(salvo);
        entrega.setTipo(TipoDestinatario.PACIENTE);
        entrega.setNome("Paciente Entrega");
        entrega.setEstado(EstadoEntrega.NOTIFICACAO_ENVIADA);
        entrega.setReceiptsPendentes("receipt-1");
        entrega.setCriadoEm(LocalDateTime.now().minusMinutes(30));
        entregaRepository.save(entrega);
        Long entregaId = entrega.getId();

        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of("receipt-1", true));

        entregaService.resolverReceiptsPendentes();

        // Append-only: a linha original é PRESERVADA (segue ENVIADA), só perde o receipt pendente.
        AgendamentoEntrega original = entregaRepository.findById(entregaId).orElseThrow();
        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, original.getEstado(), "não sobrescreve o histórico");
        assertNull(original.getReceiptsPendentes(), "receipt resolvido deixa de ser pendente");
        // E um NOVO registro ENTREGUE foi criado (2 linhas no total) — tudo PERSISTIDO.
        List<AgendamentoEntrega> linhas = entregaRepository.findByAgendamento_IdOrderByIdAsc(agendamentoId);
        assertEquals(2, linhas.size(), "criação + promoção = 2 registros");
        assertEquals(EstadoEntrega.NOTIFICACAO_ENTREGUE, linhas.get(1).getEstado(), "o mais recente é 'entregue'");
        // O resumo do agendamento reflete o estado ATUAL (última linha da pessoa).
        assertEquals(EstadoEntrega.NOTIFICACAO_ENTREGUE,
                agendamentoRepository.findById(agendamentoId).orElseThrow().getEntregaResumo());
    }

    /** Cria (e salva) um agendamento com todas as FKs mínimas; registra os ids para limpeza. */
    private Agendamento novoAgendamentoSalvo() {
        Paciente paciente = pacienteController.criar(new PacienteRequest("Paciente Entrega", null), null);
        pacienteId = paciente.getId();

        Especialidade esp = new Especialidade();
        esp.setNome("Cardiologia");
        especialidadeRepository.save(esp);
        espId = esp.getId();

        ProfissionalSaude prof = new ProfissionalSaude();
        prof.setNome("Dra. Teste");
        profissionalRepository.save(prof);
        profId = prof.getId();

        Procedimento proc = new Procedimento();
        proc.setNome("Consulta");
        proc.setHorasCancelamento(24);
        proc.setHorasNps(48);
        procedimentoRepository.save(proc);
        procId = proc.getId();

        Unidade uni = new Unidade();
        uni.setNome("UBS Teste");
        unidadeRepository.save(uni);
        uniId = uni.getId();

        Agendamento a = new Agendamento();
        a.setDataHora(LocalDateTime.now().plusDays(1));
        a.setEspecialidade(esp);
        a.setProfissionalSaude(prof);
        a.setProcedimento(proc);
        a.setPaciente(paciente);
        a.setUnidadeSaude(uni);
        a.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Agendamento salvo = agendamentoRepository.save(a);
        agendamentoId = salvo.getId();
        return salvo;
    }
}
