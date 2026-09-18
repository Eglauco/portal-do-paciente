package com.example.pop.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.unidade.UnidadeFaq;
import com.example.pop.unidade.UnidadeFaqRepository;

/**
 * Camada TRANSACIONAL do primeiro atendimento por IA: carrega o contexto (FAQ + ficha + histórico),
 * aplica a resposta ou o escalonamento, e trata o pedido de "falar com humano". O trabalho lento
 * (a chamada ao Claude) fica no {@link ChatIaOrchestrator} (assíncrono); aqui só há acesso a banco,
 * curto e transacional. Todo write revalida as condições (a IA pode ter sido desligada, ou um humano
 * pode ter assumido durante os segundos da chamada) — só age se a IA ainda estiver no comando.
 */
@Service
public class ChatIaService {

    /** Encaminhamento padrão quando a IA escala sem uma frase própria. */
    static final String MENSAGEM_PADRAO_ESCALONAMENTO =
            "Vou encaminhar você para um de nossos atendentes, que vai continuar o seu atendimento por aqui. 🙂";
    /** Confirmação quando o próprio paciente toca em "Falar com humano". */
    static final String MENSAGEM_PEDIDO_HUMANO =
            "Certo! Vou te encaminhar para um de nossos atendentes. 🙂";
    /** Despedida padrão quando a IA resolve (paciente sem mais dúvidas) sem uma frase própria. */
    static final String MENSAGEM_PADRAO_RESOLUCAO =
            "Fico à disposição! Se precisar de mais alguma coisa, é só chamar. 🙂";

    private final ChatRepository chatRepository;
    private final MensagemRepository mensagemRepository;
    private final ConfiguracaoService configuracaoService;
    private final UnidadeFaqRepository unidadeFaqRepository;
    private final FichaPacienteService fichaPacienteService;
    private final ChatService chatService;
    private final int historicoMaximo;

    public ChatIaService(ChatRepository chatRepository, MensagemRepository mensagemRepository,
            ConfiguracaoService configuracaoService, UnidadeFaqRepository unidadeFaqRepository,
            FichaPacienteService fichaPacienteService, ChatService chatService,
            @Value("${anthropic.chat.historico-maximo:12}") int historicoMaximo) {
        this.chatRepository = chatRepository;
        this.mensagemRepository = mensagemRepository;
        this.configuracaoService = configuracaoService;
        this.unidadeFaqRepository = unidadeFaqRepository;
        this.fichaPacienteService = fichaPacienteService;
        this.chatService = chatService;
        this.historicoMaximo = historicoMaximo;
    }

    /** Contexto da conversa para a IA (dados desanexados, seguros de usar fora da transação). */
    public record ContextoIa(String nomeUnidade, String faq, String ficha, List<Mensagem> historico) {
    }

    /**
     * Carrega o contexto se — e só se — a IA deve atuar agora: config global ligada, conversa sem
     * responsável humano, IA não encerrada e a unidade tem FAQ. Caso contrário, vazio (não atua).
     */
    @Transactional(readOnly = true)
    public Optional<ContextoIa> carregarContexto(Long chatId) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (!iaAtiva(chat)) {
            return Optional.empty();
        }
        Long unidadeId = chat.getUnidadeSaude().getId();
        List<UnidadeFaq> itens = unidadeFaqRepository.findByUnidadeIdOrderByOrdemAscIdAsc(unidadeId);
        String faq = itens.stream()
                .map(f -> "P: " + f.getPergunta().trim() + "\nR: " + f.getResposta().trim())
                .collect(Collectors.joining("\n\n"));
        String ficha = fichaPacienteService.montar(chat.getPaciente().getId(), unidadeId);

        List<Mensagem> todas = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(chatId);
        List<Mensagem> historico = todas.size() <= historicoMaximo
                ? todas
                : new ArrayList<>(todas.subList(todas.size() - historicoMaximo, todas.size()));

        return Optional.of(new ContextoIa(chat.getUnidadeSaude().getNome(), faq, ficha, historico));
    }

    /** Persiste a resposta da IA (revalidando que ela ainda está no comando). */
    @Transactional
    public void aplicarResposta(Long chatId, String texto) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (!iaNoComando(chat)) {
            return; // um humano assumiu, ou a IA foi encerrada, durante a chamada
        }
        if (texto == null || texto.isBlank()) {
            chatService.escalarParaHumano(chat, MENSAGEM_PADRAO_ESCALONAMENTO);
        } else {
            chatService.enviarComoIa(chat, texto);
        }
    }

    /** Escala para humano (revalidando). {@code texto} é a frase de encaminhamento (ou nula = padrão). */
    @Transactional
    public void aplicarEscalonamento(Long chatId, String texto) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (!iaNoComando(chat)) {
            return;
        }
        chatService.escalarParaHumano(chat, (texto == null || texto.isBlank()) ? MENSAGEM_PADRAO_ESCALONAMENTO : texto);
    }

    /** Resolve a conversa quando o paciente não tem mais dúvidas (revalidando). {@code texto} = despedida. */
    @Transactional
    public void aplicarResolucao(Long chatId, String texto) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (!iaNoComando(chat)) {
            return;
        }
        chatService.resolverPelaIa(chat, (texto == null || texto.isBlank()) ? MENSAGEM_PADRAO_RESOLUCAO : texto);
    }

    /** O paciente tocou em "Falar com humano": encerra a IA e manda para a fila (se a IA estava atuando). */
    @Transactional
    public void pacientePedeHumano(Long chatId) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (!iaAtiva(chat)) {
            return; // IA não está atuando (já é humano, já escalou, ou IA desligada): nada a fazer
        }
        chatService.escalarParaHumano(chat, MENSAGEM_PEDIDO_HUMANO);
    }

    /**
     * A IA deve atuar nesta conversa? Basta o toggle global ligado + sem atendente humano + IA não
     * encerrada. O FAQ da unidade é OPCIONAL (enriquece as respostas): sem FAQ, a IA atende com a
     * ficha do paciente e escala quando faltar informação.
     */
    private boolean iaAtiva(Chat chat) {
        return iaNoComando(chat) && iaHabilitadaGlobal();
    }

    /** A IA ainda "manda" na conversa: existe, sem responsável humano e não encerrada. */
    private boolean iaNoComando(Chat chat) {
        return chat != null && chat.getResponsavel() == null && !chat.isIaEncerrada();
    }

    /** Kill switch global do chat com IA. Erro de config → desligado (o humano atende). */
    private boolean iaHabilitadaGlobal() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.APP_CHAT_IA_HABILITADO);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
