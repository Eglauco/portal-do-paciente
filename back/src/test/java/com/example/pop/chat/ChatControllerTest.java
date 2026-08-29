package com.example.pop.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class ChatControllerTest {

    @Autowired
    private ChatController controller;

    @Test
    void listaChatsSemeados() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, false, 0, 50);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos os chats semeados");
    }

    @Test
    void filtraPorStatusNaoLida() {
        // Garante o cenário: paciente envia e o chat fica NAO_LIDA (independe do seed).
        controller.enviarComoPaciente(2L, new MensagemRequest("Tenho uma dúvida sobre o resultado."));

        Pagina<ChatResponse> pagina = controller.listar(null, null, StatusChat.NAO_LIDA, false, 0, 50);
        assertTrue(pagina.totalElements() >= 1);
        assertTrue(pagina.content().stream().allMatch(c -> c.status() == StatusChat.NAO_LIDA));
        assertTrue(pagina.content().stream().anyMatch(c -> c.naoLidas() > 0));
    }

    @Test
    void naoResolvidasNaoTrazResolvidos() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, true, 0, 50);
        assertFalse(pagina.content().isEmpty());
        assertTrue(pagina.content().stream().noneMatch(c -> c.status() == StatusChat.RESOLVIDO));
    }

    @Test
    void detalheTrazMensagensOrdenadas() {
        ChatDetalheResponse detalhe = controller.buscar(1L).getBody();
        assertNotNull(detalhe);
        // >= 3: o chat semeado é compartilhado com o app em dev, então pode ter
        // mensagens extras de uso real; o que importa é a ordem (mais antiga primeiro,
        // do paciente) e trazer ao menos as mensagens semeadas.
        assertTrue(detalhe.mensagens().size() >= 3, "esperado ao menos as mensagens semeadas");
        assertEquals(RemetenteMensagem.PACIENTE, detalhe.mensagens().get(0).remetente());
    }

    @Test
    void pacienteEnviaMensagemEChatFicaNaoLida() {
        ChatDetalheResponse antes = controller.buscar(4L).getBody();
        assertNotNull(antes);
        int qtdAntes = antes.mensagens().size();

        ChatDetalheResponse depois = controller
                .enviarComoPaciente(4L, new MensagemRequest("Olá, tenho uma dúvida sobre o exame.")).getBody();
        assertNotNull(depois);
        assertEquals(qtdAntes + 1, depois.mensagens().size());

        MensagemResponse ultima = depois.mensagens().get(depois.mensagens().size() - 1);
        assertEquals(RemetenteMensagem.PACIENTE, ultima.remetente());
        assertFalse(ultima.lida());
        assertEquals(StatusChat.NAO_LIDA, depois.status());
    }
}
