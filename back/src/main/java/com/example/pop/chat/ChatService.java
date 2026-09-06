package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ref;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

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
    private final UsuarioRepository usuarioRepository;
    private final ResponsavelRepository responsavelRepository;
    private final PacienteAcessoService acessoService;
    private final ChatLogService chatLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushService pushService;
    private final StorageService storageService;

    public ChatService(ChatRepository repository, MensagemRepository mensagemRepository,
            PacienteRepository pacienteRepository, UnidadeRepository unidadeRepository,
            UsuarioRepository usuarioRepository, ResponsavelRepository responsavelRepository,
            PacienteAcessoService acessoService, ChatLogService chatLogService,
            SimpMessagingTemplate messagingTemplate, PushService pushService, StorageService storageService) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.usuarioRepository = usuarioRepository;
        this.responsavelRepository = responsavelRepository;
        this.acessoService = acessoService;
        this.chatLogService = chatLogService;
        this.messagingTemplate = messagingTemplate;
        this.pushService = pushService;
        this.storageService = storageService;
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
     * true quando o paciente é ALCANÇÁVEL no app: a própria sessão está amarrada a
     * um aparelho OU algum responsável dele tem uma conta com aparelho ativo (o
     * responsável responde por ele). Enquanto alcançável, o admin pode abrir/enviar
     * e o app do responsável recebe as mensagens. Delega ao serviço de acesso, que
     * conhece a modelagem de conta/responsável.
     */
    public boolean pacienteUsandoApp(Paciente paciente) {
        return acessoService.pacienteAlcancavel(paciente);
    }

    /**
     * Registra uma mensagem do paciente na conversa e publica em tempo real. Quando
     * quem envia é um responsável agindo pelo perfil dependente, {@code responsavel}
     * é o cadastro dele — gravado na mensagem e mostrado como marcador; nulo quando é
     * o próprio paciente.
     */
    public Chat enviarComoPaciente(Chat chat, String texto, String clienteId, Responsavel responsavel) {
        if (jaEnviada(chat.getId(), clienteId)) {
            return chat; // idempotente: reenvio da mesma mensagem não duplica
        }
        Long responsavelId = responsavel != null ? responsavel.getId() : null;
        Mensagem salva = criar(chat, RemetenteMensagem.PACIENTE, texto, clienteId, false, null, responsavelId);

        // O paciente enviou: a unidade ainda não visualizou.
        chat.setStatus(StatusChat.NAO_LIDA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        publicar(chat.getId(), salva, responsavel != null ? responsavel.getNome() : null);
        return chat;
    }

    /**
     * Registra uma mensagem da unidade (atendente), publica e notifica o paciente.
     * Só o atendente RESPONSÁVEL pela conversa pode enviar (regra "assumir conversa").
     */
    public Chat enviarComoUnidade(Chat chat, String texto, String clienteId, Long usuarioId) {
        if (jaEnviada(chat.getId(), clienteId)) {
            return chat; // idempotente: mensagem já entregue quando o paciente ainda usava o app
        }
        // Regra "assumir conversa": só o responsável envia.
        Usuario responsavel = chat.getResponsavel();
        if (responsavel == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assuma a conversa para poder responder.");
        }
        if (!responsavel.getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O usuário " + responsavel.getNome() + " é responsável pela conversa.");
        }
        // Paciente deixou de usar o app (sessão revogada/trocou de aparelho): a
        // mensagem não chegaria a ninguém — bloqueia o envio da unidade.
        if (!pacienteUsandoApp(chat.getPaciente())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O paciente não está mais utilizando o aplicativo no celular.");
        }
        marcarMensagensDoPacienteComoLidas(chat.getId());
        // O remetente é o próprio atendente (garantido pelo guard acima); sem responsável do paciente.
        Mensagem salva = criar(chat, RemetenteMensagem.UNIDADE, texto, clienteId, true, responsavel, null);

        StatusChat statusAntes = chat.getStatus();
        chat.setStatus(StatusChat.EM_ATENDIMENTO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        // Audita a mudança de status quando ela ocorre por um envio (ex.: o
        // responsável reabriu e voltou a responder → AGUARDANDO → EM_ATENDIMENTO).
        if (statusAntes != StatusChat.EM_ATENDIMENTO) {
            chatLogService.registrar(chat, TipoLogChat.STATUS_ALTERADO, responsavel.getId(), null,
                    statusAntes, StatusChat.EM_ATENDIMENTO);
        }

        publicar(chat.getId(), salva, null);
        // Notifica o paciente (o app suprime se ele já estiver nessa conversa).
        pushService.notificarNovaMensagem(chat);
        return chat;
    }

    /**
     * O atendente assume (ou transfere para si) a conversa: passa a ser o único
     * que pode enviar. Publica a troca em tempo real para bloquear o anterior na
     * hora e para as listas/telas refletirem o novo responsável.
     */
    public Chat assumir(Chat chat, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        chat.setResponsavel(usuario);
        chat.setStatus(StatusChat.EM_ATENDIMENTO);
        chat.setAtualizadoEm(LocalDateTime.now());
        Chat salvo = repository.save(chat);
        ResponsavelEvento evento = new ResponsavelEvento(salvo.getId(), usuario.getId(), usuario.getNome());
        aposCommit(() -> {
            messagingTemplate.convertAndSend("/topic/chat/" + salvo.getId() + "/responsavel", evento);
            messagingTemplate.convertAndSend("/topic/chats", new ChatEvento(salvo.getId()));
        });
        return salvo;
    }

    /** true se já existe uma mensagem com este clienteId no chat (evita duplicar em reenvios). */
    private boolean jaEnviada(Long chatId, String clienteId) {
        return clienteId != null && !clienteId.isBlank()
                && mensagemRepository.findByChatIdAndClienteId(chatId, clienteId).isPresent();
    }

    private Mensagem criar(Chat chat, RemetenteMensagem remetente, String texto, String clienteId, boolean lida,
            Usuario usuario, Long responsavelId) {
        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        mensagem.setRemetente(remetente);
        mensagem.setUsuario(usuario);
        mensagem.setResponsavelId(responsavelId);
        mensagem.setTexto(texto.trim());
        mensagem.setEnviadaEm(LocalDateTime.now());
        mensagem.setLida(lida);
        mensagem.setClienteId(clienteId != null && !clienteId.isBlank() ? clienteId.trim() : null);
        return mensagemRepository.save(mensagem);
    }

    /**
     * Publica a nova mensagem em tempo real (conversa + sinal de lista) SÓ APÓS o
     * commit: assim um eco nunca sai de um envio que sofreu rollback (evita bolha
     * fantasma no back-office) e um eco recebido implica mensagem já persistida.
     */
    public void publicar(Long chatId, Mensagem mensagem, String responsavelNome) {
        MensagemResponse payload = MensagemResponse.from(mensagem, responsavelNome);
        aposCommit(() -> {
            messagingTemplate.convertAndSend("/topic/chat/" + chatId, payload);
            messagingTemplate.convertAndSend("/topic/chats", new ChatEvento(chatId));
        });
    }

    /** Executa a ação após o commit da transação atual (ou imediatamente, se não houver). */
    private void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
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
        aposCommit(() -> messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/entregue", new EntregaEvento(chatId)));
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
                storageService.urlFotoPaciente(chat.getPaciente().getFotoUrl()),
                new Ref(chat.getUnidadeSaude().getId(), chat.getUnidadeSaude().getNome()),
                chat.getStatus(),
                chat.getStatus().getDescricao(),
                ultima != null ? ultima.getTexto() : null,
                ultima != null ? ultima.getRemetente() : null,
                ultima != null ? ultima.getEnviadaEm() : null,
                naoLidas,
                chat.getAtualizadoEm(),
                chat.getResponsavel() != null ? chat.getResponsavel().getId() : null,
                chat.getResponsavel() != null ? chat.getResponsavel().getNome() : null);
    }

    public ChatDetalheResponse toDetalhe(Chat chat) {
        List<Mensagem> lista = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(chat.getId());
        Map<Long, String> nomes = nomesDosResponsaveis(lista);
        List<MensagemResponse> mensagens = lista.stream()
                .map(m -> MensagemResponse.from(m,
                        m.getResponsavelId() == null ? null : nomes.get(m.getResponsavelId())))
                .toList();
        return new ChatDetalheResponse(
                chat.getId(),
                new Ref(chat.getPaciente().getId(), chat.getPaciente().getNome()),
                storageService.urlFotoPaciente(chat.getPaciente().getFotoUrl()),
                new Ref(chat.getUnidadeSaude().getId(), chat.getUnidadeSaude().getNome()),
                chat.getStatus(),
                chat.getStatus().getDescricao(),
                pacienteUsandoApp(chat.getPaciente()),
                chat.getResponsavel() != null ? chat.getResponsavel().getId() : null,
                chat.getResponsavel() != null ? chat.getResponsavel().getNome() : null,
                mensagens);
    }

    /**
     * Nome de cada responsável referenciado pelas mensagens, resolvido em uma única
     * consulta (evita N+1 ao montar o detalhe). Vazio quando nenhuma mensagem foi
     * enviada via responsável.
     */
    private Map<Long, String> nomesDosResponsaveis(List<Mensagem> mensagens) {
        Set<Long> ids = mensagens.stream()
                .map(Mensagem::getResponsavelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return responsavelRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Responsavel::getId, Responsavel::getNome));
    }

    /** Sinal leve para as telas de lista recarregarem. */
    public record ChatEvento(Long chatId) {
    }

    /** Evento de troca de responsável (bloqueia o atendente anterior em tempo real). */
    public record ResponsavelEvento(Long chatId, Long responsavelId, String responsavelNome) {
    }
}
