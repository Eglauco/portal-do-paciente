package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.pop.common.Ref;

/**
 * Regras e mapeamentos de chat compartilhados entre o lado da unidade
 * ({@link ChatController}) e o lado do paciente ({@link MeuChatController}).
 */
@Service
public class ChatService {

    private final ChatRepository repository;
    private final MensagemRepository mensagemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ChatRepository repository, MensagemRepository mensagemRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /** Registra uma mensagem do paciente na conversa e publica em tempo real. */
    public Chat enviarComoPaciente(Chat chat, String texto) {
        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        mensagem.setRemetente(RemetenteMensagem.PACIENTE);
        mensagem.setTexto(texto.trim());
        mensagem.setEnviadaEm(LocalDateTime.now());
        mensagem.setLida(false);
        Mensagem salva = mensagemRepository.save(mensagem);

        // O paciente enviou: a unidade ainda não visualizou.
        chat.setStatus(StatusChat.NAO_LIDA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        publicar(chat.getId(), salva);
        return chat;
    }

    /** Publica a nova mensagem em tempo real (conversa + sinal de lista). */
    public void publicar(Long chatId, Mensagem mensagem) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatId, MensagemResponse.from(mensagem));
        messagingTemplate.convertAndSend("/topic/chats", new ChatEvento(chatId));
    }

    /**
     * Marca como ENTREGUES as mensagens da unidade que ainda não haviam chegado
     * ao paciente (chamado quando o app do paciente recebe/abre a conversa) e
     * avisa o back-office em tempo real (2º "check").
     */
    public void marcarEntregue(Long chatId) {
        List<Mensagem> pendentes = mensagemRepository
                .findByChatIdAndRemetenteAndEntregueFalse(chatId, RemetenteMensagem.UNIDADE);
        if (pendentes.isEmpty()) {
            return;
        }
        pendentes.forEach(m -> m.setEntregue(true));
        mensagemRepository.saveAll(pendentes);
        messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/entregue", new EntregaEvento(chatId));
    }

    /** Sinal de que as mensagens da conversa foram entregues ao paciente. */
    public record EntregaEvento(Long chatId) {
    }

    /** Marca as mensagens do paciente como lidas (lado da unidade). */
    public void marcarMensagensDoPacienteComoLidas(Long chatId) {
        List<Mensagem> naoLidas = mensagemRepository
                .findByChatIdAndRemetenteAndLidaFalse(chatId, RemetenteMensagem.PACIENTE);
        naoLidas.forEach(m -> m.setLida(true));
        if (!naoLidas.isEmpty()) {
            mensagemRepository.saveAll(naoLidas);
        }
    }

    public ChatResponse toResponse(Chat chat) {
        Mensagem ultima = mensagemRepository.findFirstByChatIdOrderByEnviadaEmDesc(chat.getId());
        long naoLidas = mensagemRepository
                .countByChatIdAndRemetenteAndLidaFalse(chat.getId(), RemetenteMensagem.PACIENTE);
        return new ChatResponse(
                chat.getId(),
                new Ref(chat.getPaciente().getId(), chat.getPaciente().getNome()),
                new Ref(chat.getUnidadeSaude().getId(), chat.getUnidadeSaude().getNome()),
                chat.getStatus(),
                chat.getStatus().getDescricao(),
                ultima != null ? ultima.getTexto() : null,
                ultima != null ? ultima.getRemetente() : null,
                ultima != null ? ultima.getEnviadaEm() : null,
                naoLidas,
                chat.getAtualizadoEm());
    }

    public ChatDetalheResponse toDetalhe(Chat chat) {
        List<MensagemResponse> mensagens = mensagemRepository
                .findByChatIdOrderByEnviadaEmAsc(chat.getId())
                .stream().map(MensagemResponse::from).toList();
        return new ChatDetalheResponse(
                chat.getId(),
                new Ref(chat.getPaciente().getId(), chat.getPaciente().getNome()),
                new Ref(chat.getUnidadeSaude().getId(), chat.getUnidadeSaude().getNome()),
                chat.getStatus(),
                chat.getStatus().getDescricao(),
                mensagens);
    }

    /** Sinal leve para as telas de lista recarregarem. */
    public record ChatEvento(Long chatId) {
    }
}
