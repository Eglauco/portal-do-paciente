package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

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
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/** Filtros da busca de agendamentos (nome do paciente, especialidade, período). */
@SpringBootTest
class AgendamentoBuscaIntegrationTest {

    @Autowired private AgendamentoController agendamentoController;
    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private ProcedimentoRepository procedimentoRepository;
    @Autowired private ProfissionalSaudeRepository profissionalRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private PacienteController pacienteController;
    @Autowired private PacienteRepository pacienteRepository;

    private final List<Long> agendamentos = new ArrayList<>();
    private final List<Long> pacientes = new ArrayList<>();
    private Long espCardio;
    private Long espDermato;
    private Long profId;
    private Long procId;
    private Long uniId;

    @AfterEach
    void limpar() {
        agendamentos.forEach(agendamentoRepository::deleteById);
        pacientes.forEach(pacienteRepository::deleteById);
        if (espCardio != null) especialidadeRepository.deleteById(espCardio);
        if (espDermato != null) especialidadeRepository.deleteById(espDermato);
        if (profId != null) profissionalRepository.deleteById(profId);
        if (procId != null) procedimentoRepository.deleteById(procId);
        if (uniId != null) unidadeRepository.deleteById(uniId);
    }

    @Test
    void filtraPorNomeEspecialidadeEPeriodo() {
        // Nomes de especialidade ÚNICOS isolam dos agendamentos semeados (a busca agora é por NOME).
        String nomeCardio = "CardioBuscaZZ";
        String nomeDermato = "DermatoBuscaZZ";
        espCardio = salvarEspecialidade(nomeCardio);
        espDermato = salvarEspecialidade(nomeDermato);
        profId = salvarProfissional();
        procId = salvarProcedimento();
        uniId = salvarUnidade();

        LocalDateTime hoje = LocalDateTime.now();
        Agendamento a1 = criar("Alice Souza", espCardio, hoje.plusHours(2));       // cardio, hoje
        Agendamento a2 = criar("Bruno Lima", espDermato, hoje.plusDays(5));         // dermato, +5 dias

        List<Agendamento> soCardio = buscar(null, nomeCardio, null, null);
        assertEquals(1, soCardio.size());
        assertEquals(a1.getId(), soCardio.get(0).getId());
        List<Agendamento> soDermato = buscar(null, nomeDermato, null, null);
        assertEquals(1, soDermato.size());
        assertEquals(a2.getId(), soDermato.get(0).getId());

        // Busca PARCIAL da especialidade (case-insensitive): "cardiobusca" casa "CardioBuscaZZ".
        assertEquals(1, buscar(null, "cardiobusca", null, null).size());

        // Nome parcial do paciente, dentro do escopo da especialidade (por nome).
        assertEquals(1, buscar("alice", nomeCardio, null, null).size());
        assertTrue(buscar("bruno", nomeCardio, null, null).isEmpty(), "a1 é a Alice, não o Bruno");

        // Período: a1 está hoje; a janela futura exclui.
        assertEquals(1, buscar(null, nomeCardio, hoje.minusHours(1), hoje.plusDays(1)).size());
        assertTrue(buscar(null, nomeCardio, hoje.plusDays(10), hoje.plusDays(20)).isEmpty(), "fora da janela");
    }

    /**
     * Regressão do fim-do-dia: filtrando pela data = hoje, um agendamento à meia-noite do dia
     * SEGUINTE deve ficar FORA do dia. Passa pelo controller (exercita fimDoDia()), que o teste
     * por repositório não cobre. Com LocalTime.MAX (nanos) o driver arredondava para 00:00:00 do
     * dia seguinte e o incluía indevidamente.
     */
    @Test
    void periodoAteNaoIncluiMeiaNoiteDoDiaSeguinte() {
        String nomeCardio = "CardioDiaZZ";
        espCardio = salvarEspecialidade(nomeCardio);
        profId = salvarProfissional();
        procId = salvarProcedimento();
        uniId = salvarUnidade();

        LocalDate hoje = LocalDate.now();
        Agendamento doDia = criar("Carla Dia", espCardio, hoje.atTime(10, 0));
        Agendamento meiaNoiteSeguinte = criar("Diego MeiaNoite", espCardio, hoje.plusDays(1).atStartOfDay());

        List<Long> ids = agendamentoController
                .listar(null, null, nomeCardio, null, null, hoje, null, null, 0, 50)
                .content().stream().map(AgendamentoResponse::id).toList();

        assertTrue(ids.contains(doDia.getId()), "o agendamento de hoje deve aparecer");
        assertTrue(!ids.contains(meiaNoiteSeguinte.getId()), "meia-noite do dia seguinte está fora do período");
    }

    /**
     * O '_' digitado na busca por nome deve casar literalmente (não como curinga LIKE).
     * Passa pelo controller (exercita padraoNome() + a cláusula escape da query).
     */
    @Test
    void buscaPorNomeTrataSublinhadoComoLiteral() {
        String nomeDermato = "DermatoSubZZ";
        espDermato = salvarEspecialidade(nomeDermato);
        profId = salvarProfissional();
        procId = salvarProcedimento();
        uniId = salvarUnidade();

        Agendamento comSublinhado = criar("Zeta_Um", espDermato, LocalDateTime.now().plusHours(3));
        criar("ZetaXUm", espDermato, LocalDateTime.now().plusHours(4)); // o '_' NÃO pode casar o 'X'

        List<Long> ids = agendamentoController
                .listar(null, "zeta_um", nomeDermato, null, null, null, null, null, 0, 50)
                .content().stream().map(AgendamentoResponse::id).toList();

        assertEquals(1, ids.size(), "o '_' literal casa só 'Zeta_Um', não 'ZetaXUm'");
        assertEquals(comSublinhado.getId(), ids.get(0));
    }

    private List<Agendamento> buscar(String nome, String especialidadeNome, LocalDateTime de, LocalDateTime ate) {
        // search espera os padrões LIKE já montados (minúsculos, com curingas).
        return agendamentoRepository.search(null, padrao(nome), padrao(especialidadeNome), null, null, de, ate, null,
                Pageable.unpaged()).getContent();
    }

    private static String padrao(String v) {
        return v == null ? null : "%" + v.toLowerCase() + "%";
    }

    private Agendamento criar(String nomePaciente, Long especialidadeId, LocalDateTime dataHora) {
        Paciente p = pacienteController.criar(new PacienteRequest(nomePaciente, null), null);
        pacientes.add(p.getId());
        Agendamento a = new Agendamento();
        a.setDataHora(dataHora);
        a.setEspecialidade(especialidadeRepository.findById(especialidadeId).orElseThrow());
        a.setProfissionalSaude(profissionalRepository.findById(profId).orElseThrow());
        a.setProcedimento(procedimentoRepository.findById(procId).orElseThrow());
        a.setPaciente(p);
        a.setUnidadeSaude(unidadeRepository.findById(uniId).orElseThrow());
        a.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Agendamento salvo = agendamentoRepository.save(a);
        agendamentos.add(salvo.getId());
        return salvo;
    }

    private Long salvarEspecialidade(String nome) {
        Especialidade e = new Especialidade();
        e.setNome(nome);
        return especialidadeRepository.save(e).getId();
    }

    private Long salvarProfissional() {
        ProfissionalSaude p = new ProfissionalSaude();
        p.setNome("Dra. Teste");
        return profissionalRepository.save(p).getId();
    }

    private Long salvarProcedimento() {
        Procedimento p = new Procedimento();
        p.setNome("Consulta");
        p.setHorasCancelamento(24);
        p.setHorasNps(48);
        return procedimentoRepository.save(p).getId();
    }

    private Long salvarUnidade() {
        Unidade u = new Unidade();
        u.setNome("UBS Teste");
        return unidadeRepository.save(u).getId();
    }
}
