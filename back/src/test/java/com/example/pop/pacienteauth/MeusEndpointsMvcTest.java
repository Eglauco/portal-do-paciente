package com.example.pop.pacienteauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.chat.Chat;
import com.example.pop.chat.ChatRepository;
import com.example.pop.nps.Nps;
import com.example.pop.nps.NpsRepository;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.prontuario.Prontuario;
import com.example.pop.prontuario.ProntuarioRepository;

import jakarta.servlet.Filter;

/**
 * Fase 4: os endpoints /meu/** exigem token do paciente e só entregam/alteram os
 * dados do próprio paciente. Um paciente recém-criado (sem registros) não deve
 * conseguir ler/alterar agendamento, prontuário, chat ou NPS de terceiros (IDOR -> 404).
 */
@SpringBootTest
class MeusEndpointsMvcTest {

    private static final String TEL = "11955554444";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private Filter springSecurityFilterChain;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ProntuarioRepository prontuarioRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private NpsRepository npsRepository;

    private MockMvc mvc;
    private Long pacienteId;
    private String token;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Meu", TEL)).getId();
        String codigo = pacienteController.gerarCodigo(pacienteId).getBody().codigo();
        token = authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-meu")).token();
    }

    @AfterEach
    void limpar() {
        pacienteRepository.deleteById(pacienteId);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    @Test
    void meusEndpointsExigemTokenErespondemComSessao() throws Exception {
        // Sem token → 401.
        mvc.perform(get("/meu/agendamentos")).andExpect(status().isUnauthorized());
        // Com token → 200 (lista do próprio paciente, mesmo que vazia).
        mvc.perform(get("/meu/agendamentos").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/meu/prontuarios").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/meu/nps").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(get("/meu/chats").header("Authorization", bearer())).andExpect(status().isOk());
    }

    @Test
    void naoCancelaAgendamentoDeOutroPaciente() throws Exception {
        Agendamento alheio = agendamentoRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem agendamento semeado para testar IDOR");
        mvc.perform(post("/meu/agendamentos/" + alheio.getId() + "/cancelar").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoLeProntuarioDeOutroPaciente() throws Exception {
        Prontuario alheio = prontuarioRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem prontuário semeado para testar IDOR");
        mvc.perform(get("/meu/prontuarios/" + alheio.getId()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoLeChatDeOutroPaciente() throws Exception {
        Chat alheio = chatRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem chat semeado para testar IDOR");
        mvc.perform(get("/meu/chats/" + alheio.getId()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoLeNpsDeOutroPaciente() throws Exception {
        Nps alheio = npsRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem NPS semeado para testar IDOR");
        mvc.perform(get("/meu/nps/" + alheio.getId()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }
}
