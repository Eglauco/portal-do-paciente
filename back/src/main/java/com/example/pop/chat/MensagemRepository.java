package com.example.pop.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findByChatIdOrderByEnviadaEmAsc(Long chatId);

    Mensagem findFirstByChatIdOrderByEnviadaEmDesc(Long chatId);

    long countByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);

    List<Mensagem> findByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);
}
