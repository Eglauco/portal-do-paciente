package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    /** Idempotência: encontra a mensagem já gravada com este clienteId (evita duplicar em reenvios). */
    Optional<Mensagem> findByChatIdAndClienteId(Long chatId, String clienteId);

    /** Thread completa; join fetch no atendente para exibir o nome sem N+1. */
    @Query("select m from Mensagem m left join fetch m.usuario where m.chat.id = :chatId order by m.enviadaEm asc")
    List<Mensagem> findByChatIdOrderByEnviadaEmAsc(@Param("chatId") Long chatId);

    Mensagem findFirstByChatIdOrderByEnviadaEmDesc(Long chatId);

    long countByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);

    List<Mensagem> findByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);

    /** Mensagens de um remetente ainda não entregues ao destinatário (para os "checks"). */
    List<Mensagem> findByChatIdAndRemetenteAndEntregueFalse(Long chatId, RemetenteMensagem remetente);

    // ===================== Dashboard (agregações) =====================

    /** Mensagens da unidade no período agrupadas por remetente (PACIENTE/UNIDADE). */
    @Query("""
            select m.remetente, count(m) from Mensagem m
            where (:unidadeId is null or m.chat.unidadeSaude.id = :unidadeId)
              and m.enviadaEm between :inicio and :fim
            group by m.remetente
            """)
    List<Object[]> agruparPorRemetentePeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
