package com.example.pop.chat;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import com.example.pop.paciente.PacienteAcessoService;

import jakarta.validation.Valid;

/**
 * Conversas do paciente logado (app). O paciente vem do token; abrir/enviar em
 * conversa de outro paciente responde 404. (A autorização do WebSocket é tratada
 * à parte — ver Fase 4B.)
 */
@RestController
@RequestMapping("/meu/chats")
public class MeuChatController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ChatRepository repository;
    private final ChatService chatService;
    private final PacienteAcessoService acessoService;

    public MeuChatController(ChatRepository repository, ChatService chatService, PacienteAcessoService acessoService) {
        this.repository = repository;
        this.chatService = chatService;
        this.acessoService = acessoService;
    }

    /** Lista as conversas do paciente logado (mais recentes primeiro). */
    @GetMapping
    public Pagina<ChatResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "atualizadoEm"));
        Page<Chat> resultado = repository.search(pacienteId, null, null, false, StatusChat.RESOLVIDO, pageable);
        List<ChatResponse> content = resultado.getContent().stream().map(chatService::toResponse).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe (histórico) de uma conversa do paciente logado. */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ChatDetalheResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return chatService.toDetalhe(minhaConversa(jwt, id));
    }

    /** Envia uma mensagem do paciente logado na conversa dele. */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ChatDetalheResponse enviar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MensagemRequest request) {
        Chat chat = chatService.enviarComoPaciente(minhaConversa(jwt, id), request.texto(), request.clienteId());
        return chatService.toDetalhe(chat);
    }

    /** Confirma que as mensagens da unidade chegaram no aparelho do paciente (2º "check"). */
    @PostMapping("/{id}/entregue")
    @Transactional
    public void confirmarEntrega(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Chat chat = minhaConversa(jwt, id);
        chatService.marcarEntregue(chat.getId());
    }

    /** Carrega a conversa garantindo que é do paciente logado (404 caso contrário). */
    private Chat minhaConversa(Jwt jwt, Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return repository.findByIdAndPacienteId(id, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"));
    }
}
