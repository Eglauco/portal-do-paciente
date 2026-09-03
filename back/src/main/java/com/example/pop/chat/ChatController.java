package com.example.pop.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.UsuarioRepository;

import jakarta.validation.Valid;

/** Lado da UNIDADE (back-office). O lado do paciente está em {@link MeuChatController}. */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ChatRepository repository;
    private final ChatService chatService;
    private final ChatLogService chatLogService;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UnidadeRepository unidadeRepository;
    private final ExportacaoService exportacaoService;

    public ChatController(ChatRepository repository, ChatService chatService, ChatLogService chatLogService,
            PacienteRepository pacienteRepository, UsuarioRepository usuarioRepository,
            UnidadeRepository unidadeRepository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.chatService = chatService;
        this.chatLogService = chatLogService;
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.unidadeRepository = unidadeRepository;
        this.exportacaoService = exportacaoService;
    }

    /** Linha do tempo de auditoria da conversa (só ações de atendentes). */
    @GetMapping("/{id}/logs")
    @Transactional(readOnly = true)
    public List<ChatLogResponse> logs(@PathVariable Long id) {
        obter(id); // 404 se a conversa não existe
        return chatLogService.listar(id);
    }

    @GetMapping
    public Pagina<ChatResponse> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(required = false) StatusChat status,
            @RequestParam(defaultValue = "false") boolean naoResolvidas,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "atualizadoEm"));
        Page<Chat> resultado = repository.search(pacienteId, unidadeId, responsavelId, status, naoResolvidas,
                StatusChat.RESOLVIDO, pageable);
        List<ChatResponse> content = resultado.getContent().stream().map(chatService::toResponse).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta as conversas que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(required = false) StatusChat status,
            @RequestParam(defaultValue = "false") boolean naoResolvidas) {
        List<ChatResponse> dados = repository
                .search(pacienteId, unidadeId, responsavelId, status, naoResolvidas, StatusChat.RESOLVIDO,
                        Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Chat::getAtualizadoEm).reversed())
                .map(chatService::toResponse)
                .toList();
        List<ColunaExport<ChatResponse>> colunas = colunasChat();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Chats",
                        filtrosChat(pacienteId, unidadeId, responsavelId, status, naoResolvidas), colunas, dados)
                : exportacaoService.excel("Chats", colunas, dados);
        String nome = "chats-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosChat(Long pacienteId, Long unidadeId, Long responsavelId,
            StatusChat status, boolean naoResolvidas) {
        String paciente = pacienteId == null ? "Todos"
                : pacienteRepository.findById(pacienteId).map(p -> p.getNome()).orElse("#" + pacienteId);
        String responsavel = responsavelId == null ? "Todos"
                : usuarioRepository.findById(responsavelId).map(u -> u.getNome()).orElse("#" + responsavelId);
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        return List.of(
                new FiltroAplicado("Paciente", paciente),
                new FiltroAplicado("Responsável", responsavel),
                new FiltroAplicado("Status", status != null ? status.getDescricao() : "Todos"),
                new FiltroAplicado("Unidade", unidade),
                new FiltroAplicado("Apenas não resolvidas", naoResolvidas ? "Sim" : "Não"));
    }

    private static List<ColunaExport<ChatResponse>> colunasChat() {
        return List.of(
                ColunaExport.de("Paciente", c -> c.paciente().nome()),
                ColunaExport.de("Unidade", c -> c.unidadeSaude().nome()),
                ColunaExport.de("Status", ChatResponse::statusDescricao),
                ColunaExport.de("Responsável", c -> c.responsavelNome() != null ? c.responsavelNome() : "Sem responsável"),
                ColunaExport.de("Não lidas", c -> String.valueOf(c.naoLidas())),
                ColunaExport.de("Última mensagem de", c -> remetente(c.ultimaMensagemDe())),
                ColunaExport.de("Última mensagem", ChatResponse::ultimaMensagem),
                ColunaExport.de("Última mensagem em",
                        c -> c.ultimaMensagemEm() == null ? "" : c.ultimaMensagemEm().format(DATA_HORA)),
                ColunaExport.de("Atualizado em",
                        c -> c.atualizadoEm() == null ? "" : c.atualizadoEm().format(DATA_HORA)));
    }

    private static String remetente(RemetenteMensagem de) {
        if (de == null) {
            return "";
        }
        return de == RemetenteMensagem.PACIENTE ? "Paciente" : "Unidade";
    }

    /**
     * Abre a conversa do paciente na unidade. Reutiliza a existente (1 por
     * paciente+unidade) ou cria uma nova; 422 se o paciente não estiver usando
     * o app (sem sessão amarrada a um aparelho).
     */
    @PostMapping
    public ResponseEntity<ChatDetalheResponse> abrir(@Valid @RequestBody AbrirConversaRequest request) {
        ChatService.AberturaConversa abertura = chatService.abrirOuCriar(request.pacienteId(), request.unidadeId());
        HttpStatus status = abertura.criado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(chatService.toDetalhe(abertura.chat()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(chat -> ResponseEntity.ok(chatService.toDetalhe(chat)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Unidade abriu a conversa: marca as mensagens do paciente como lidas. */
    @PostMapping("/{id}/visualizar")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> visualizar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = obter(id);
        StatusChat antes = chat.getStatus();
        chatService.marcarMensagensDoPacienteComoLidas(chat.getId());
        if (chat.getStatus() == StatusChat.NAO_LIDA) {
            chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
            repository.save(chat);
        }
        chatLogService.registrar(chat, TipoLogChat.VISUALIZOU, uidDoToken(jwt), null, antes, chat.getStatus());
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    /** Atendente assume (ou transfere para si) a conversa: passa a ser o responsável. */
    @PostMapping("/{id}/assumir")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> assumir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = obter(id);
        StatusChat antes = chat.getStatus();
        Long uid = uidDoToken(jwt);
        chat = chatService.assumir(chat, uid);
        chatLogService.registrar(chat, TipoLogChat.ASSUMIU, uid, null, antes, chat.getStatus());
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    /**
     * Transfere a conversa para outro atendente: o usuário indicado passa a ser o
     * responsável (e o anterior é bloqueado ao vivo). Reaproveita a mesma regra do
     * "assumir", só que apontando para o usuário escolhido no corpo da requisição.
     */
    @PostMapping("/{id}/transferir")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> transferir(@PathVariable Long id,
            @Valid @RequestBody TransferirRequest request, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = obter(id);
        StatusChat antes = chat.getStatus();
        Long origem = uidDoToken(jwt);
        chat = chatService.assumir(chat, request.usuarioId());
        chatLogService.registrar(chat, TipoLogChat.TRANSFERIU, origem, request.usuarioId(), antes, chat.getStatus());
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    /** Envia uma mensagem em nome da unidade (só o atendente responsável pela conversa). */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> enviar(@PathVariable Long id,
            @Valid @RequestBody MensagemRequest request, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = chatService.enviarComoUnidade(obter(id), request.texto(), request.clienteId(), uidDoToken(jwt));
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    private Long uidDoToken(Jwt jwt) {
        return jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }

    /** Envia uma mensagem em nome do paciente (compat.; o app usa /meu/chats/{id}/mensagem). */
    @PostMapping("/{id}/mensagem-paciente")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> enviarComoPaciente(@PathVariable Long id,
            @Valid @RequestBody MensagemRequest request) {
        Chat chat = chatService.enviarComoPaciente(obter(id), request.texto(), request.clienteId());
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    @PostMapping("/{id}/resolver")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> resolver(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = obter(id);
        StatusChat antes = chat.getStatus();
        chat.setStatus(StatusChat.RESOLVIDO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        chatLogService.registrar(chat, TipoLogChat.RESOLVEU, uidDoToken(jwt), null, antes, StatusChat.RESOLVIDO);
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    @PostMapping("/{id}/reabrir")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> reabrir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Chat chat = obter(id);
        StatusChat antes = chat.getStatus();
        chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        chatLogService.registrar(chat, TipoLogChat.REABRIU, uidDoToken(jwt), null, antes, StatusChat.AGUARDANDO_RESPOSTA);
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    private Chat obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat não encontrado"));
    }
}
