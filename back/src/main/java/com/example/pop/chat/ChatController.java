package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import jakarta.validation.Valid;

/** Lado da UNIDADE (back-office). O lado do paciente está em {@link MeuChatController}. */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ChatRepository repository;
    private final ChatService chatService;

    public ChatController(ChatRepository repository, ChatService chatService) {
        this.repository = repository;
        this.chatService = chatService;
    }

    @GetMapping
    public Pagina<ChatResponse> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) StatusChat status,
            @RequestParam(defaultValue = "false") boolean naoResolvidas,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "atualizadoEm"));
        Page<Chat> resultado = repository.search(pacienteId, unidadeId, status, naoResolvidas,
                StatusChat.RESOLVIDO, pageable);
        List<ChatResponse> content = resultado.getContent().stream().map(chatService::toResponse).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
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
    public ResponseEntity<ChatDetalheResponse> visualizar(@PathVariable Long id) {
        Chat chat = obter(id);
        chatService.marcarMensagensDoPacienteComoLidas(chat.getId());
        if (chat.getStatus() == StatusChat.NAO_LIDA) {
            chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
            repository.save(chat);
        }
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    /** Envia uma mensagem em nome da unidade (operador). */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> enviar(@PathVariable Long id, @Valid @RequestBody MensagemRequest request) {
        Chat chat = chatService.enviarComoUnidade(obter(id), request.texto(), request.clienteId());
        return ResponseEntity.ok(chatService.toDetalhe(chat));
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
    public ResponseEntity<ChatDetalheResponse> resolver(@PathVariable Long id) {
        Chat chat = obter(id);
        chat.setStatus(StatusChat.RESOLVIDO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    @PostMapping("/{id}/reabrir")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> reabrir(@PathVariable Long id) {
        Chat chat = obter(id);
        chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        return ResponseEntity.ok(chatService.toDetalhe(chat));
    }

    private Chat obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat não encontrado"));
    }
}
