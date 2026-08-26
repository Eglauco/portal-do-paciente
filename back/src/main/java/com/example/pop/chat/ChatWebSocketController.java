package com.example.pop.chat;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Recebe eventos de "digitando…" do cliente (STOMP) e retransmite para os
 * inscritos da conversa. É efêmero: nada é persistido.
 */
@Controller
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{id}/digitando")
    public void digitando(@DestinationVariable Long id, DigitandoEvento evento) {
        messagingTemplate.convertAndSend("/topic/chat/" + id + "/digitando", evento);
    }
}
