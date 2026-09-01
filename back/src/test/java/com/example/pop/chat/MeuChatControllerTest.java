package com.example.pop.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.example.pop.common.Ref;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.unidade.UnidadeRepository;

@SpringBootTest
class MeuChatControllerTest {

    private static final String TEL = "11955559999";

    @Autowired
    private MeuChatController meuController;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private UnidadeRepository unidadeRepository;
    @Autowired
    private JwtDecoder jwtDecoder;

    private Long pacienteId;
    private Long unidadeId;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> {
            chatRepository.findByPacienteIdAndUnidadeSaudeId(p.getId(), unidadeAtual())
                    .ifPresent(c -> chatRepository.deleteById(c.getId()));
            pacienteRepository.deleteById(p.getId());
        });
        pacienteId = pacienteController.criar(new PacienteRequest("Chat Paciente", TEL)).getId();
        String codigo = pacienteController.gerarCodigo(pacienteId).getBody().codigo();
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-meuchat")).token());
        unidadeId = unidadeAtual();
    }

    private Long unidadeAtual() {
        return unidadeRepository.findAll().get(0).getId();
    }

    @AfterEach
    void limpar() {
        chatRepository.findByPacienteIdAndUnidadeSaudeId(pacienteId, unidadeId)
                .ifPresent(c -> chatRepository.deleteById(c.getId()));
        pacienteRepository.deleteById(pacienteId);
    }

    @Test
    void unidadesDisponiveis() {
        List<Ref> unidades = meuController.unidades();
        assertFalse(unidades.isEmpty());
        assertTrue(unidades.stream().anyMatch(u -> u.id().equals(unidadeId)));
    }

    @Test
    void abrirCriaEReutilizaMesmaConversa() {
        ChatDetalheResponse primeira = meuController.abrir(jwt, new AbrirMinhaConversaRequest(unidadeId));
        assertNotNull(primeira.id());
        assertEquals(unidadeId, primeira.unidadeSaude().id());

        // Segunda abertura na MESMA unidade reutiliza a conversa (1 por paciente+unidade).
        ChatDetalheResponse segunda = meuController.abrir(jwt, new AbrirMinhaConversaRequest(unidadeId));
        assertEquals(primeira.id(), segunda.id());

        // Confere que só existe uma conversa desse paciente nessa unidade.
        long total = chatRepository.findAll().stream()
                .filter(c -> c.getPaciente().getId().equals(pacienteId)
                        && c.getUnidadeSaude().getId().equals(unidadeId))
                .count();
        assertEquals(1, total);
    }
}
