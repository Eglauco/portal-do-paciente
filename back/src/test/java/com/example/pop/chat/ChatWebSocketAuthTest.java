package com.example.pop.chat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;

/**
 * Fase 4B: segurança do WebSocket do chat. CONNECT exige token válido; um
 * paciente só assina a própria conversa; o admin assina qualquer uma.
 */
@SpringBootTest
class ChatWebSocketAuthTest {

    private static final String TEL = "11944443333";

    @Autowired
    private ChatChannelInterceptor interceptor;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ChatRepository chatRepository;

    private Long pacienteId;
    private String token;

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente WS", TEL)).getId();
        String codigo = pacienteController.gerarCodigo(pacienteId).getBody().codigo();
        token = authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-ws")).token();
    }

    @AfterEach
    void limpar() {
        pacienteRepository.deleteById(pacienteId);
    }

    private Message<byte[]> frame(StompCommand comando, String destino, String authHeader, Principal usuario) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        if (destino != null) {
            accessor.setDestination(destino);
        }
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        if (usuario != null) {
            accessor.setUser(usuario);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectSemTokenEhRejeitado() {
        Message<byte[]> m = frame(StompCommand.CONNECT, null, null, null);
        assertThrows(MessagingException.class, () -> interceptor.preSend(m, null));
    }

    @Test
    void connectComTokenAutenticaOPaciente() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<byte[]> m = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(m, null);

        assertTrue(accessor.getUser() instanceof ChatPrincipal, "deveria ter definido o principal");
        assertEquals(pacienteId, ((ChatPrincipal) accessor.getUser()).id());
    }

    @Test
    void pacienteNaoAssinaConversaDeOutro() {
        Chat alheio = chatRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem chat semeado para testar");
        Principal paciente = new ChatPrincipal("PACIENTE:" + pacienteId, "PACIENTE", pacienteId);
        Message<byte[]> m = frame(StompCommand.SUBSCRIBE, "/topic/chat/" + alheio.getId(), null, paciente);
        assertThrows(MessagingException.class, () -> interceptor.preSend(m, null));
    }

    @Test
    void adminAssinaQualquerConversa() {
        Chat alheio = chatRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(alheio != null, "sem chat semeado para testar");
        Principal admin = new ChatPrincipal("ADMIN:1", "ADMIN", 1L);
        Message<byte[]> m = frame(StompCommand.SUBSCRIBE, "/topic/chat/" + alheio.getId(), null, admin);
        assertDoesNotThrow(() -> interceptor.preSend(m, null));
    }
}
