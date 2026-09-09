package com.example.pop.notificacaoadmin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoAdminRepository extends JpaRepository<NotificacaoAdmin, Long> {

    /** Notificações do admin na unidade ativa, mais recentes primeiro. */
    Page<NotificacaoAdmin> findByUsuarioIdAndUnidadeIdOrderByCriadoEmDesc(Long usuarioId, Long unidadeId,
            Pageable pageable);

    /** Contador do sino: não lidas do admin na unidade ativa. */
    long countByUsuarioIdAndUnidadeIdAndLidaFalse(Long usuarioId, Long unidadeId);

    /** Carrega garantindo que a notificação é do admin logado (escopo/ownership). */
    Optional<NotificacaoAdmin> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Marca TODAS as não lidas do admin na unidade ativa (botão "marcar todas"). */
    @Modifying
    @Query("update NotificacaoAdmin n set n.lida = true, n.lidaEm = :agora "
            + "where n.usuarioId = :usuarioId and n.unidadeId = :unidadeId and n.lida = false")
    int marcarTodasLidas(@Param("usuarioId") Long usuarioId, @Param("unidadeId") Long unidadeId,
            @Param("agora") LocalDateTime agora);

    /** Admins que já têm uma NÃO LIDA para o mesmo evento (dedup: não repete o mesmo alvo). */
    @Query("select n.usuarioId from NotificacaoAdmin n "
            + "where n.tipo = :tipo and n.referenciaId = :referenciaId and n.lida = false")
    List<Long> usuariosComNaoLidaDe(@Param("tipo") TipoNotificacaoAdmin tipo,
            @Param("referenciaId") Long referenciaId);
}
