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
        Pagina<ChatResponse> pagina = controller.listar(null, null, StatusChat.NAO_LIDA, false, 0, 50);
        assertTrue(pagina.totalElements() >= 2);
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
        assertEquals(3, detalhe.mensagens().size());
        assertEquals(RemetenteMensagem.PACIENTE, detalhe.mensagens().get(0).remetente());
    }
}
