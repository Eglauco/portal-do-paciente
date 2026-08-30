package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ref;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.push.PushService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Regras e mapeamentos de chat compartilhados entre o lado da unidade
 * ({@link ChatController}) e o lado do paciente ({@link MeuChatController}).
 */
@Service
public class ChatService {

    private final ChatRepository repository;
    private final MensagemRepository mensagemRepository;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushService pushService;

    public ChatService(ChatRepository repository, MensagemRepository mensagemRepository,
            PacienteRepository pacienteRepository, UnidadeRepository unidadeRepository,
            SimpMessagingTemplate messagingTemplate, PushService pushService) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.messagingTemplate = messagingTemplate;
        this.pushService = pushService;
    }

    /**
     * Abre a conversa do paciente na unidade, criando-a se ainda não existir
     * (regra: 1 conversa por paciente+unidade). Se já existir, devolve a mesma
     * (o paciente pode ter deixado de usar o app — mesmo assim reabrimos o
     * histórico). Só bloqueia a CRIAÇÃO de uma conversa nova quando o paciente
     * não está usando o app (sem sessão amarrada a um aparelho): sem isso, ele
     * nunca receberia as mensagens.
     *
     * <p>Sem {@code @Transactional} de propósito: cada operação de repositório
     * roda na própria transação, então a violação do índice único numa corrida
     * é capturada e resolvida relendo a conversa já criada (uma transação
     * marcada para rollback não conseguiria reconsultar).
     */
    public AberturaConversa abrirOuCriar(Long pacienteId, Long unidadeId) {
        Optional<Chat> existente = repository.findByPacienteIdAndUnidadeSaudeId(pacienteId, unidadeId);
        if (existente.isPresent()) {
            return new AberturaConversa(existente.get(), false);
        }

        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado"));
        if (!pacienteUsandoApp(paciente)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O paciente não está usando o aplicativo no celular.");
        }
        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade não encontrada"));

        Chat novo = new Chat();
        novo.setPaciente(paciente);
        novo.setUnidadeSaude(unidade);
        novo.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
        LocalDateTime agora = LocalDateTime.now();
        novo.setCriadoEm(agora);
        novo.setAtualizadoEm(agora);
        try {
            return new AberturaConversa(repository.save(novo), true);
        } catch (DataIntegrityViolationException corrida) {
            // Outro pedido criou o mesmo par entre a checagem e o insert: devolve o existente.
            Chat criadoPorOutro = repository.findByPacienteIdAndUnidadeSaudeId(pacienteId, unidadeId)
                    .orElseThrow(() -> corrida);
            return new AberturaConversa(criadoPorOutro, false);
        }
    }

    /** Resultado do "abrir ou criar": a conversa e se ela foi criada agora. */
    public record AberturaConversa(Chat chat, boolean criado) {
    }

    /**
     * true quando o paciente tem uma sessão do app amarrada a um aparelho — ou
     * seja, está de fato usando o app. {@code ativo} sozinho não basta: fica
     * true assim que o admin gera o código de ativação, antes de o paciente
     * ativar no celular. Sem uma sessão, ele não recebe as mensagens.
     */
    public boolean pacienteUsandoApp(Paciente paciente) {
        return paciente != null && paciente.isAtivo() && paciente.getDispositivoAtivo() != null;
    }

    /** Registra uma mensagem do paciente na conversa e publica em tempo real. */
    public Chat enviarComoPaciente(Chat chat, String texto, String clienteId) {
        if (jaEnviada(chat.getId(), clienteId)) {
            return chat; // idempotente: reenvio da mesma mensagem não duplica
        }
        Mensagem salva = criar(chat, RemetenteMensagem.PACIENTE, texto, clienteId, false);

        // O paciente enviou: a unidade ainda não visualizou.
        chat.setStatus(StatusChat.NAO_LIDA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        publicar(chat.getId(), salva);
        return chat;
    }

    /** Registra uma mensagem da unidade (operador), publica e notifica o paciente. */
    public Chat enviarComoUnidade(Chat chat, String texto, String clienteId) {
        if (jaEnviada(chat.getId(), clienteId)) {
            return chat; // idempotente: mensagem já entregue quando o paciente ainda usava o app
        }
        // Paciente deixou de usar o app (sessão revogada/trocou de aparelho): a
        // mensagem não chegaria a ninguém — bloqueia o envio da unidade.
        if (!pacienteUsandoApp(chat.getPaciente())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O paciente não está mais utilizando o aplicativo no celular.");
        }
        marcarMensagensDoPacienteComoLidas(chat.getId());
        Mensagem salva = criar(chat, RemetenteMensagem.UNIDADE, texto, clienteId, true);

        chat.setStatus(StatusChat.EM_ATENDIMENTO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        publicar(chat.getId(), salva);
        // Notifica o paciente (o app suprime se ele já estiver nessa conversa).
        pushService.notificarNovaMensagem(chat);
        return chat;
    }

    /** true se já existe uma mensagem com este clienteId no chat (evita duplicar em reenvios). */
    private boolean jaEnviada(Long chatId, String clienteId) {
        return clienteId != null && !clienteId.isBlank()
                && mensagemRepository.findByChatIdAndClienteId(chatId, clienteId).isPresent();
    }

    private Mensagem criar(Chat chat, RemetenteMensagem remetente, String texto, String clienteId, boolean lida) {
        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        mensagem.setRemetente(remetente);
        mensagem.setTexto(texto.trim());
        mensagem.setEnviadaEm(LocalDateTime.now());
        mensagem.setLida(lida);
        mensagem.setClienteId(clienteId != null && !clienteId.isBlank() ? clienteId.trim() : null);
        return mensagemRepository.save(mensagem);
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
        // Publica SÓ APÓS o commit: o back-office reage ao evento recarregando o
        // retrato da conversa; se publicássemos antes do commit, esse retrato
        // poderia ler o "entregue" ainda como false (2º check voltaria a 1).
        publicarEntregueAposCommit(chatId);
    }

    private void publicarEntregueAposCommit(Long chatId) {
        EntregaEvento evento = new EntregaEvento(chatId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/entregue", evento);
                }
            });
        } else {
            messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/entregue", evento);
        }
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
                pacienteUsandoApp(chat.getPaciente()),
                mensagens);
    }

    /** Sinal leve para as telas de lista recarregarem. */
    public record ChatEvento(Long chatId) {
    }
}
