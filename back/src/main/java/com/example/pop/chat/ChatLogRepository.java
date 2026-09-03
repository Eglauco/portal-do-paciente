package com.example.pop.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    /** Linha do tempo da conversa (mais antigo primeiro); join fetch nos usuários p/ evitar N+1. */
    @Query("""
            select l from ChatLog l
            left join fetch l.usuario
            left join fetch l.destino
            where l.chat.id = :chatId
            order by l.criadoEm asc, l.id asc
            """)
    List<ChatLog> findByChatIdOrderByCriadoEmAsc(@Param("chatId") Long chatId);
}
