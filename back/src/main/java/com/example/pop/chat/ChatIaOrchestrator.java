package com.example.pop.chat;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orquestra o primeiro atendimento por IA em BACKGROUND (assíncrono), para não travar o envio do
 * paciente. Fluxo: carrega o contexto (curto, transacional) → mostra "escrevendo…" ao paciente →
 * chama o Claude (lento) → aplica a resposta ou o escalonamento. FAIL-OPEN: qualquer erro escala
 * para atendimento humano.
 */
@Service
public class ChatIaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatIaOrchestrator.class);

    private final ChatIaService chatIaService;
    private final AssistenteChatIaService assistente;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatIaOrchestrator(ChatIaService chatIaService, AssistenteChatIaService assistente,
            SimpMessagingTemplate messagingTemplate) {
        this.chatIaService = chatIaService;
        this.assistente = assistente;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Processa a última mensagem do paciente com a IA, se ela deve atuar. Chamado APÓS o commit da
     * mensagem do paciente. Roda no executor "chatIaExecutor" (não bloqueia o request do paciente).
     */
    @Async("chatIaExecutor")
    public void processarSePreciso(Long chatId) {
        try {
            Optional<ChatIaService.ContextoIa> ctxOpt = chatIaService.carregarContexto(chatId);
            if (ctxOpt.isEmpty()) {
                return; // IA não deve atuar nesta conversa
            }
            ChatIaService.ContextoIa ctx = ctxOpt.get();

            // "escrevendo…" para o paciente enquanto a IA gera a resposta (o app limpa ao chegar msg).
            messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/digitando",
                    new DigitandoEvento(RemetenteMensagem.UNIDADE));

            AssistenteChatIaService.RespostaIa resposta =
                    assistente.responder(ctx.nomeUnidade(), ctx.faq(), ctx.ficha(), ctx.historico());

            switch (resposta.acao()) {
                case RESPONDER -> chatIaService.aplicarResposta(chatId, resposta.texto());
                case RESOLVER -> chatIaService.aplicarResolucao(chatId, resposta.texto());
                case ESCALAR -> chatIaService.aplicarEscalonamento(chatId, resposta.texto());
            }
        } catch (RuntimeException e) {
            log.warn("Falha no atendimento por IA do chat {}; escalando para humano (fail-open): {}",
                    chatId, e.toString());
            try {
                chatIaService.aplicarEscalonamento(chatId, null);
            } catch (RuntimeException ex) {
                log.warn("Falha ao escalar o chat {} após erro da IA: {}", chatId, ex.toString());
            }
        }
    }
}
