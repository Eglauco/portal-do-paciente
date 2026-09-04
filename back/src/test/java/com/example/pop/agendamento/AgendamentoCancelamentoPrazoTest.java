package com.example.pop.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.verificacao.VerificacaoService;

@SpringBootTest
class AgendamentoCancelamentoPrazoTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String TEL = "11955558888";

    @Autowired
    private MeusAgendamentosController meuController;
    @Autowired
    private AgendamentoController agendamentoController;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ProcedimentoRepository procedimentoRepository;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    private Long procedimentoId;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> {
            apagarAgendamentos(p.getId());
            pacienteRepository.deleteById(p.getId());
        });
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Prazo", TEL)).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-prazo")).token());
        // Procedimento com prazo de 24h de antecedência.
        procedimentoId = procedimentoRepository.save(new Procedimento(null, "Proc Prazo Teste", null, 24, 0)).getId();
    }

    @AfterEach
    void limpar() {
        apagarAgendamentos(pacienteId);
        pacienteRepository.deleteById(pacienteId);
        procedimentoRepository.deleteById(procedimentoId);
    }

    private void apagarAgendamentos(Long pid) {
        agendamentoRepository.findAll().stream()
                .filter(a -> a.getPaciente().getId().equals(pid))
                .forEach(a -> agendamentoRepository.deleteById(a.getId()));
    }

    private Long criarAgendamento(LocalDateTime dataHora) {
        return agendamentoController.criar(
                new AgendamentoRequest(dataHora, 1L, 1L, procedimentoId, pacienteId, 1L, null)).id();
    }

    @Test
    void confirmadoEDentroDoPrazoCancela() {
        // Consulta daqui a 48h, prazo de 24h → limite ainda no futuro → cancela.
        Long id = criarAgendamento(LocalDateTime.now(FUSO).plusHours(48));
        meuController.confirmar(jwt, id); // → PACIENTE_CONFIRMOU
        AgendamentoResponse resp = meuController.cancelar(jwt, id);
        assertEquals(StatusAgendamento.CANCELADO_PELO_PACIENTE, resp.statusAgendamento());
    }

    @Test
    void confirmadoMasForaDoPrazoBloqueiaCom409() {
        // Consulta daqui a 12h, prazo de 24h → o limite (consulta - 24h) já passou → 409.
        Long id = criarAgendamento(LocalDateTime.now(FUSO).plusHours(12));
        meuController.confirmar(jwt, id);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> meuController.cancelar(jwt, id)).getStatusCode().value());
    }

    @Test
    void naoConfirmadoNaoPodeCancelar() {
        // Agendamento apenas aguardando (não confirmado) → 409, mesmo dentro do prazo.
        Long id = criarAgendamento(LocalDateTime.now(FUSO).plusHours(48));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> meuController.cancelar(jwt, id)).getStatusCode().value());
    }
}
