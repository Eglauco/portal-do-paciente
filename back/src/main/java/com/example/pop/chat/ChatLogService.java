package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pop.usuario.UsuarioRepository;

/** Auditoria da conversa: grava cada ação de atendente e lê a linha do tempo. */
@Service
public class ChatLogService {

    private final ChatLogRepository repository;
    private final UsuarioRepository usuarioRepository;

    public ChatLogService(ChatLogRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra um evento na transação da ação. A transição de status só é gravada
     * quando de fato mudou (antes != depois). destinoId só no caso de transferência.
     */
    public void registrar(Chat chat, TipoLogChat tipo, Long usuarioId, Long destinoId,
            StatusChat antes, StatusChat depois) {
        ChatLog log = new ChatLog();
        log.setChat(chat);
        log.setTipo(tipo);
        // findById (e não getReferenceById): se o ator não existir mais — ex.: usuário
        // excluído ainda com token válido — o log fica com ator nulo em vez de estourar a
        // FK e derrubar (rollback) a ação legítima. Auditoria não pode quebrar a operação.
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(log::setUsuario);
        }
        if (destinoId != null) {
            usuarioRepository.findById(destinoId).ifPresent(log::setDestino);
        }
        if (antes != null && depois != null && antes != depois) {
            log.setStatusAnterior(antes);
            log.setStatusNovo(depois);
        }
        log.setCriadoEm(LocalDateTime.now());
        repository.save(log);
    }

    public List<ChatLogResponse> listar(Long chatId) {
        return repository.findByChatIdOrderByCriadoEmAsc(chatId).stream().map(ChatLogResponse::from).toList();
    }
}
