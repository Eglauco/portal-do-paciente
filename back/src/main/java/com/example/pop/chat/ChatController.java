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
import com.example.pop.common.Ref;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ChatRepository repository;
    private final MensagemRepository mensagemRepository;

    public ChatController(ChatRepository repository, MensagemRepository mensagemRepository) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
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
        List<ChatResponse> content = resultado.getContent().stream().map(this::toResponse).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(chat -> ResponseEntity.ok(toDetalhe(chat)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Marca as mensagens do paciente como lidas ao abrir a conversa. */
    @PostMapping("/{id}/visualizar")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> visualizar(@PathVariable Long id) {
        Chat chat = obter(id);
        marcarMensagensDoPacienteComoLidas(chat.getId());
        if (chat.getStatus() == StatusChat.NAO_LIDA) {
            chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
            repository.save(chat);
        }
        return ResponseEntity.ok(toDetalhe(chat));
    }

    /** Envia uma mensagem em nome da unidade (operador). */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> enviar(@PathVariable Long id, @Valid @RequestBody MensagemRequest request) {
        Chat chat = obter(id);
        marcarMensagensDoPacienteComoLidas(chat.getId());

        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        mensagem.setRemetente(RemetenteMensagem.UNIDADE);
        mensagem.setTexto(request.texto().trim());
        mensagem.setEnviadaEm(LocalDateTime.now());
        mensagem.setLida(true);
        mensagemRepository.save(mensagem);

        chat.setStatus(StatusChat.EM_ATENDIMENTO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);

        return ResponseEntity.ok(toDetalhe(chat));
    }

    @PostMapping("/{id}/resolver")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> resolver(@PathVariable Long id) {
        Chat chat = obter(id);
        chat.setStatus(StatusChat.RESOLVIDO);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        return ResponseEntity.ok(toDetalhe(chat));
    }

    @PostMapping("/{id}/reabrir")
    @Transactional
    public ResponseEntity<ChatDetalheResponse> reabrir(@PathVariable Long id) {
        Chat chat = obter(id);
        chat.setStatus(StatusChat.AGUARDANDO_RESPOSTA);
        chat.setAtualizadoEm(LocalDateTime.now());
        repository.save(chat);
        return ResponseEntity.ok(toDetalhe(chat));
    }

    // ---------- helpers ----------

    private Chat obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat não encontrado"));
    }

    private void marcarMensagensDoPacienteComoLidas(Long chatId) {
        List<Mensagem> naoLidas = mensagemRepository
                .findByChatIdAndRemetenteAndLidaFalse(chatId, RemetenteMensagem.PACIENTE);
        naoLidas.forEach(m -> m.setLida(true));
        if (!naoLidas.isEmpty()) {
            mensagemRepository.saveAll(naoLidas);
        }
    }

    private ChatResponse toResponse(Chat chat) {
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

    private ChatDetalheResponse toDetalhe(Chat chat) {
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
}
