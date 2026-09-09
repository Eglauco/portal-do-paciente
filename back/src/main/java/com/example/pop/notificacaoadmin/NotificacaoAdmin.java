package com.example.pop.notificacaoadmin;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notificação do back-office destinada a UM administrador (fan-out: uma linha por
 * admin elegível no momento do evento). {@code usuarioId}/{@code unidadeId} são
 * guardados como ids simples (padrão do {@code Comentario}), evitando carregar o
 * grafo do usuário. A visibilidade do sino é filtrada por (usuarioId + unidade ativa).
 */
@Entity
@Table(name = "notificacao_admin")
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Admin dono desta notificação (o "lida" é por administrador). */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Unidade do evento — o sino mostra só as da unidade ATIVA do admin. */
    @Column(name = "unidade_id", nullable = false)
    private Long unidadeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacaoAdmin tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 400)
    private String corpo;

    /** Rota do front para onde o clique leva (ex.: "/sau/123"). */
    @Column(nullable = false, length = 200)
    private String rota;

    /** Id do alvo (manifestação, comentário, NPS) — usado para deduplicar não-lidas do mesmo evento. */
    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(nullable = false)
    private boolean lida;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "lida_em")
    private LocalDateTime lidaEm;
}
