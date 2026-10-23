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

    /**
     * Thread completa; join fetch no atendente e na mensagem citada (+ atendente dela) para
     * exibir os nomes e o trecho da citação sem N+1 nem LazyInitialization.
     */
    @Query("select m from Mensagem m left join fetch m.usuario "
            + "left join fetch m.respondeA ra left join fetch ra.usuario "
            + "where m.chat.id = :chatId order by m.enviadaEm asc")
    List<Mensagem> findByChatIdOrderByEnviadaEmAsc(@Param("chatId") Long chatId);

    /** Mensagem citada com o atendente já carregado (para montar o trecho da citação ao publicar). */
    @Query("select m from Mensagem m left join fetch m.usuario where m.id = :id")
    Optional<Mensagem> findByIdComUsuario(@Param("id") Long id);

    Mensagem findFirstByChatIdOrderByEnviadaEmDesc(Long chatId);

    /** Última mensagem de um remetente (ex.: UNIDADE) — para saber se o paciente já leu tudo. */
    Optional<Mensagem> findFirstByChatIdAndRemetenteOrderByEnviadaEmDesc(Long chatId, RemetenteMensagem remetente);

    /** O responsável enviou alguma mensagem no chat (lançamento)? Trava a remoção do responsável. */
    boolean existsByResponsavelId(Long responsavelId);

    long countByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);

    List<Mensagem> findByChatIdAndRemetenteAndLidaFalse(Long chatId, RemetenteMensagem remetente);

    /** Mensagens de um remetente ainda não entregues ao destinatário (para os "checks"). */
    List<Mensagem> findByChatIdAndRemetenteAndEntregueFalse(Long chatId, RemetenteMensagem remetente);

    /** Mensagens de um remetente ainda sem data de visualização (para carimbar o recibo "lido"). */
    List<Mensagem> findByChatIdAndRemetenteAndVisualizadaEmIsNull(Long chatId, RemetenteMensagem remetente);

    /**
     * Mensagens de um remetente ainda não entregues em TODAS as conversas do paciente
     * (entrega global: o app confirma o recebimento mesmo fora da tela da conversa).
     */
    @Query("select m from Mensagem m where m.chat.paciente.id = :pacienteId "
            + "and m.remetente = :remetente and m.entregue = false")
    List<Mensagem> pendentesDeEntregaDoPaciente(@Param("pacienteId") Long pacienteId,
            @Param("remetente") RemetenteMensagem remetente);

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
