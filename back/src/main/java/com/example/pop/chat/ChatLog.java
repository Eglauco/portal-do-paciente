package com.example.pop.chat;

import java.time.LocalDateTime;

import com.example.pop.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de auditoria de uma ação de atendente numa conversa (linha do tempo).
 * Só ações de staff: visualizar, assumir, transferir, resolver, reabrir e as
 * mudanças de status causadas por elas. O nome do usuário vem por JOIN (só o FK).
 */
@Entity
@Table(name = "chat_log")
@Getter
@Setter
@NoArgsConstructor
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoLogChat tipo;

    /** Quem realizou a ação (atendente). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Destino da transferência (só quando tipo = TRANSFERIU). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destino_id")
    private Usuario destino;

    /** Transição de status registrada (nulos quando a ação não mudou o status). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 30)
    private StatusChat statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", length = 30)
    private StatusChat statusNovo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
