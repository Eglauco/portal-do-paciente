package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.pop.especialidade.Especialidade;
import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.paciente.ContaApp;
import com.example.pop.paciente.ContaAppRepository;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.NivelAcessoResponsavel;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.push.Dispositivo;
import com.example.pop.push.DispositivoRepository;
import com.example.pop.push.PushService;

/** Regra de entrega (Fase 1): estado por destinatário + resumo "qualquer autorizado". */
@ExtendWith(MockitoExtension.class)
class AgendamentoEntregaServiceTest {

    @Mock private PushService pushService;
    @Mock private NotificacaoService notificacaoService;
    @Mock private ResponsavelRepository responsavelRepository;
    @Mock private ContaAppRepository contaRepository;
    @Mock private DispositivoRepository dispositivoRepository;
    @Mock private AgendamentoEntregaRepository entregaRepository;
    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private AgendamentoEntregaService service;

    @Test
    void pacienteComAppEResponsavelSemApp_enviadaEResumoEnviada() {
        Paciente paciente = paciente(1L, "Ana Titular", "11955557777");
        Agendamento a = agendamento(paciente);
        Agendamento gerenciado = agendamento(paciente); // "recarregado" (gerenciado) na transação de gravação
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(gerenciado));
        // Paciente tem conta + 1 aparelho; responsável ativo com acesso, mas sem conta (nunca logou).
        Responsavel resp = responsavel(7L, "Maria Ajuda", "11988881111", NivelAcessoResponsavel.VISUALIZAR_LANCAR);
        when(responsavelRepository.findByPaciente_Id(1L)).thenReturn(List.of(resp));
        when(contaRepository.findByTelefone("11955557777")).thenReturn(Optional.of(conta(10L)));
        when(contaRepository.findByTelefone("11988881111")).thenReturn(Optional.empty());
        when(dispositivoRepository.findByContaId(10L)).thenReturn(List.of(dispositivo("ExpoTokenA")));
        when(dispositivoRepository.findByPacienteIdAndContaIdIsNull(1L)).thenReturn(List.of());
        // A Expo aceita o token do paciente e devolve um receipt id.
        when(pushService.enviarComResultado(any(), any(), anyMap(), anyList()))
                .thenAnswer(inv -> ((List<String>) inv.getArgument(3)).stream()
                        .map(t -> new PushService.ResultadoEnvio(t, true, "rcpt-" + t)).toList());

        service.notificarNovoAgendamento(a);

        List<AgendamentoEntrega> salvas = capturarEntregas(2);
        AgendamentoEntrega doPaciente = salvas.stream().filter(x -> x.getTipo() == TipoDestinatario.PACIENTE)
                .findFirst().orElseThrow();
        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, doPaciente.getEstado());
        assertEquals("rcpt-ExpoTokenA", doPaciente.getReceiptsPendentes(), "guarda o receipt id p/ conferir a entrega");
        assertEquals(EstadoEntrega.PACIENTE_SEM_APLICATIVO, estadoDe(salvas, TipoDestinatario.RESPONSAVEL));
        // O resumo é gravado na entidade GERENCIADA (recarregada), não no 'a' destacado.
        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, gerenciado.getEntregaResumo(), "qualquer autorizado: alguém recebeu");
    }

    @Test
    void ninguemComApp_semAplicativoEnaoEnviaPush() {
        Paciente paciente = paciente(1L, "Ana Titular", "11955557777");
        Agendamento a = agendamento(paciente);
        Agendamento gerenciado = agendamento(paciente);
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(gerenciado));
        when(responsavelRepository.findByPaciente_Id(1L)).thenReturn(List.of());
        when(contaRepository.findByTelefone("11955557777")).thenReturn(Optional.empty());
        when(dispositivoRepository.findByPacienteIdAndContaIdIsNull(1L)).thenReturn(List.of());

        service.notificarNovoAgendamento(a);

        List<AgendamentoEntrega> salvas = capturarEntregas(1);
        assertEquals(EstadoEntrega.PACIENTE_SEM_APLICATIVO, salvas.get(0).getEstado());
        assertEquals(EstadoEntrega.PACIENTE_SEM_APLICATIVO, gerenciado.getEntregaResumo());
        // Sem nenhum token, nem chega a chamar a Expo.
        verify(pushService, never()).enviarComResultado(any(), any(), anyMap(), anyList());
    }

    @Test
    void pacienteComTokenRejeitado_semNotificacaoAtiva() {
        Paciente paciente = paciente(1L, "Ana Titular", "11955557777");
        Agendamento a = agendamento(paciente);
        Agendamento gerenciado = agendamento(paciente);
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(gerenciado));
        when(responsavelRepository.findByPaciente_Id(1L)).thenReturn(List.of());
        when(contaRepository.findByTelefone("11955557777")).thenReturn(Optional.of(conta(10L)));
        when(dispositivoRepository.findByContaId(10L)).thenReturn(List.of(dispositivo("ExpoTokenMorto")));
        when(dispositivoRepository.findByPacienteIdAndContaIdIsNull(1L)).thenReturn(List.of());
        // A Expo REJEITA (token morto): aceito=false, sem receipt id.
        when(pushService.enviarComResultado(any(), any(), anyMap(), anyList()))
                .thenAnswer(inv -> ((List<String>) inv.getArgument(3)).stream()
                        .map(t -> new PushService.ResultadoEnvio(t, false, null)).toList());

        service.notificarNovoAgendamento(a);

        List<AgendamentoEntrega> salvas = capturarEntregas(1);
        assertEquals(EstadoEntrega.SEM_NOTIFICACAO_ATIVA, salvas.get(0).getEstado());
        assertEquals(EstadoEntrega.SEM_NOTIFICACAO_ATIVA, gerenciado.getEntregaResumo());
    }

    @Test
    void responsavelSemAcessoNaoEhDestinatario() {
        Paciente paciente = paciente(1L, "Ana Titular", "11955557777");
        Agendamento a = agendamento(paciente);
        Responsavel semAcesso = responsavel(7L, "Sem Acesso", "11988881111", NivelAcessoResponsavel.SEM_ACESSO);
        Responsavel inativo = responsavel(8L, "Inativo", "11977772222", NivelAcessoResponsavel.VISUALIZAR);
        inativo.setAtivo(false);
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(agendamento(paciente)));
        when(responsavelRepository.findByPaciente_Id(1L)).thenReturn(List.of(semAcesso, inativo));
        when(contaRepository.findByTelefone("11955557777")).thenReturn(Optional.empty());
        when(dispositivoRepository.findByPacienteIdAndContaIdIsNull(1L)).thenReturn(List.of());

        service.notificarNovoAgendamento(a);

        // Só o paciente vira destinatário — responsável sem acesso e inativo são suprimidos.
        List<AgendamentoEntrega> salvas = capturarEntregas(1);
        assertEquals(TipoDestinatario.PACIENTE, salvas.get(0).getTipo());
    }

    @Test
    void receiptOkCriaNovoRegistroEntregueSemSobrescrever() {
        Agendamento ag = agendamento(paciente(1L, "Ana Titular", "11955557777"));
        AgendamentoEntrega enviada = entregaEnviada(ag, "r1");
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(enviada));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of("r1", true));
        when(agendamentoRepository.getReferenceById(100L)).thenReturn(ag);
        // Recompute enxerga o estado pós-append (enviada + novo entregue): o ATUAL da pessoa é ENTREGUE.
        when(entregaRepository.findByAgendamento_IdOrderByIdAsc(100L)).thenReturn(List.of(enviada, eventoEntregue(ag)));
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(ag));

        service.resolverReceiptsPendentes();

        // NÃO sobrescreve: a linha original continua ENVIADA (só perde o receipt pendente).
        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, enviada.getEstado());
        assertNull(enviada.getReceiptsPendentes(), "receipt resolvido deixa de ser pendente");
        // Cria um NOVO registro ENTREGUE (append-only, preserva o histórico).
        assertTrue(todosSaves().stream().anyMatch(
                x -> x != enviada && x.getEstado() == EstadoEntrega.NOTIFICACAO_ENTREGUE),
                "novo evento entregue gravado");
        // Resumo recalculado pelo estado ATUAL de cada pessoa (última linha) = ENTREGUE.
        assertEquals(EstadoEntrega.NOTIFICACAO_ENTREGUE, ag.getEntregaResumo(), "resumo = estado atual da pessoa");
    }

    @Test
    void receiptComErroCriaNovoRegistroSemNotificacaoAtiva() {
        Agendamento ag = agendamento(paciente(1L, "Ana Titular", "11955557777"));
        AgendamentoEntrega enviada = entregaEnviada(ag, "r1");
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(enviada));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of("r1", false));
        when(agendamentoRepository.getReferenceById(100L)).thenReturn(ag);
        when(entregaRepository.findByAgendamento_IdOrderByIdAsc(100L)).thenReturn(List.of(enviada));
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(ag));

        service.resolverReceiptsPendentes();

        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, enviada.getEstado(), "não sobrescreve o 'enviada'");
        assertTrue(todosSaves().stream().anyMatch(
                x -> x != enviada && x.getEstado() == EstadoEntrega.SEM_NOTIFICACAO_ATIVA),
                "novo evento 'sem notificação ativa'");
    }

    @Test
    void receiptAindaPendentePermaneceEnviada() {
        Agendamento ag = agendamento(paciente(1L, "Ana Titular", "11955557777"));
        AgendamentoEntrega e = entregaEnviada(ag, "r1");
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(e));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of()); // receipt ainda não pronto (ausente)

        service.resolverReceiptsPendentes();

        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, e.getEstado(), "segue aguardando");
        assertEquals("r1", e.getReceiptsPendentes(), "mantém o receipt pendente");
        verify(entregaRepository, never()).save(e); // nada mudou → não grava
    }

    @Test
    void multiplosReceiptsComUmOkCriaEventoEntregue() {
        Agendamento ag = agendamento(paciente(1L, "Ana Titular", "11955557777"));
        AgendamentoEntrega enviada = entregaEnviada(ag, "r1,r2");
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(enviada));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of("r1", false, "r2", true));
        when(agendamentoRepository.getReferenceById(100L)).thenReturn(ag);
        when(entregaRepository.findByAgendamento_IdOrderByIdAsc(100L)).thenReturn(List.of(enviada));
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(ag));

        service.resolverReceiptsPendentes();

        assertTrue(todosSaves().stream().anyMatch(
                x -> x != enviada && x.getEstado() == EstadoEntrega.NOTIFICACAO_ENTREGUE),
                "um ok entre vários basta");
    }

    @Test
    void desisteDepoisDe24hMantendoEnviada() {
        Agendamento ag = agendamento(paciente(1L, "Ana Titular", "11955557777"));
        AgendamentoEntrega e = entregaEnviada(ag, "r1");
        e.setCriadoEm(LocalDateTime.now().minusHours(25)); // além das 24h da Expo
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(e));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of()); // ainda sem receipt

        service.resolverReceiptsPendentes();

        assertEquals(EstadoEntrega.NOTIFICACAO_ENVIADA, e.getEstado(), "desistência mantém 'enviada'");
        assertNull(e.getReceiptsPendentes(), "para de consultar (sai da fila)");
        verify(entregaRepository).save(e); // só grava a limpeza do marcador; nenhum novo registro
    }

    @Test
    void responsaveisExcluidosNaoColapsamNoResumo() {
        // Dois responsáveis já EXCLUÍDOS do cadastro → responsavelId nulo (FK SET NULL) em ambos.
        Agendamento ag = agendamento(paciente(1L, "Ana", "11955557777"));
        AgendamentoEntrega pendenteA = eventoResponsavel(ag, null, "Resp A", "11911111111",
                EstadoEntrega.NOTIFICACAO_ENVIADA, "rA");
        when(entregaRepository.findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                eq(EstadoEntrega.NOTIFICACAO_ENVIADA), any())).thenReturn(List.of(pendenteA));
        when(pushService.consultarReceipts(anyList())).thenReturn(Map.of("rA", true));
        when(agendamentoRepository.getReferenceById(100L)).thenReturn(ag);
        // Estado pós-append: A (entregue, tel 111) e B (sem notif., tel 222) — ambos com responsavelId nulo.
        when(entregaRepository.findByAgendamento_IdOrderByIdAsc(100L)).thenReturn(List.of(
                pendenteA,
                eventoResponsavel(ag, null, "Resp A", "11911111111", EstadoEntrega.NOTIFICACAO_ENTREGUE, null),
                eventoResponsavel(ag, null, "Resp B", "11922222222", EstadoEntrega.SEM_NOTIFICACAO_ATIVA, null)));
        when(agendamentoRepository.findById(100L)).thenReturn(Optional.of(ag));

        service.resolverReceiptsPendentes();

        // A chave por telefone/nome mantém A e B separados: o melhor estado (ENTREGUE) vence.
        // Sem o fix, ambos cairiam em 'RESPONSAVEL|' e o resumo despencaria para SEM_NOTIFICACAO_ATIVA.
        assertEquals(EstadoEntrega.NOTIFICACAO_ENTREGUE, ag.getEntregaResumo());
    }

    // --- helpers ---

    private AgendamentoEntrega eventoResponsavel(Agendamento ag, Long respId, String nome, String tel,
            EstadoEntrega estado, String receipts) {
        AgendamentoEntrega e = new AgendamentoEntrega();
        e.setAgendamento(ag);
        e.setTipo(TipoDestinatario.RESPONSAVEL);
        e.setResponsavelId(respId);
        e.setNome(nome);
        e.setTelefone(tel);
        e.setEstado(estado);
        e.setReceiptsPendentes(receipts);
        e.setCriadoEm(LocalDateTime.now().minusMinutes(30));
        return e;
    }

    private List<AgendamentoEntrega> todosSaves() {
        ArgumentCaptor<AgendamentoEntrega> captor = ArgumentCaptor.forClass(AgendamentoEntrega.class);
        verify(entregaRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private AgendamentoEntrega entregaEnviada(Agendamento ag, String receipts) {
        AgendamentoEntrega e = new AgendamentoEntrega();
        e.setAgendamento(ag);
        e.setTipo(TipoDestinatario.PACIENTE);
        e.setNome("Ana Titular");
        e.setEstado(EstadoEntrega.NOTIFICACAO_ENVIADA);
        e.setReceiptsPendentes(receipts);
        e.setCriadoEm(LocalDateTime.now().minusMinutes(30)); // pronto (>15min) e dentro das 24h
        return e;
    }

    /** Evento posterior de ENTREGA da mesma pessoa (paciente) — simula o estado pós-append. */
    private AgendamentoEntrega eventoEntregue(Agendamento ag) {
        AgendamentoEntrega e = new AgendamentoEntrega();
        e.setAgendamento(ag);
        e.setTipo(TipoDestinatario.PACIENTE);
        e.setNome("Ana Titular");
        e.setEstado(EstadoEntrega.NOTIFICACAO_ENTREGUE);
        e.setCriadoEm(LocalDateTime.now());
        return e;
    }

    private List<AgendamentoEntrega> capturarEntregas(int esperado) {
        ArgumentCaptor<AgendamentoEntrega> captor = ArgumentCaptor.forClass(AgendamentoEntrega.class);
        verify(entregaRepository, org.mockito.Mockito.times(esperado)).save(captor.capture());
        return captor.getAllValues();
    }

    private static EstadoEntrega estadoDe(List<AgendamentoEntrega> lista, TipoDestinatario tipo) {
        return lista.stream().filter(e -> e.getTipo() == tipo).findFirst().orElseThrow().getEstado();
    }

    private Agendamento agendamento(Paciente p) {
        Especialidade esp = new Especialidade();
        esp.setNome("Cardiologia");
        Agendamento a = new Agendamento();
        a.setId(100L);
        a.setDataHora(LocalDateTime.of(2026, 9, 20, 14, 30));
        a.setEspecialidade(esp);
        a.setPaciente(p);
        return a;
    }

    private Paciente paciente(long id, String nome, String telefone) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNome(nome);
        p.setTelefone(telefone);
        return p;
    }

    private Responsavel responsavel(long id, String nome, String telefone, NivelAcessoResponsavel nivel) {
        Responsavel r = new Responsavel();
        r.setId(id);
        r.setNome(nome);
        r.setTelefone(telefone);
        r.setAtivo(true);
        r.getPermissoes().put(FuncionalidadeApp.AGENDAMENTOS, nivel);
        return r;
    }

    private ContaApp conta(long id) {
        ContaApp c = new ContaApp();
        c.setId(id);
        return c;
    }

    private Dispositivo dispositivo(String token) {
        Dispositivo d = new Dispositivo();
        d.setToken(token);
        return d;
    }
}
