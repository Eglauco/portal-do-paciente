package com.example.pop.postagem;

import java.time.LocalDateTime;

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

/** Comentário do paciente em uma postagem. */
@Entity
@Table(name = "comentario")
@Getter
@Setter
@NoArgsConstructor
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "postagem_id", nullable = false)
    private Postagem postagem;

    /** Comentário-raiz ao qual esta resposta pertence; nulo se este for um comentário-raiz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comentario_pai_id")
    private Comentario comentarioPai;

    /** Dono do comentário quando é do paciente (app). Nulo em comentários antigos ou do admin. */
    @Column(name = "paciente_id")
    private Long pacienteId;

    /** Dono do comentário quando é do admin (back-office). Nulo em comentários do paciente. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /**
     * Responsável (cadastro) que fez o comentário representando o paciente. Nulo
     * quando o próprio paciente comentou (ou em comentários do admin/antigos).
     */
    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Quando foi editado pela última vez (nulo se nunca editado). */
    @Column(name = "editado_em")
    private LocalDateTime editadoEm;

    /**
     * Estado de moderação por IA. PUBLICADO por padrão (postagem sem validação, ou aprovado).
     * PENDENTE = potencialmente ofensivo/não validado (oculto do público até o admin decidir).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_moderacao", nullable = false, length = 20)
    private StatusModeracao statusModeracao = StatusModeracao.PUBLICADO;

    /** Motivo da IA quando ficou pendente (auditoria; exibido ao admin). */
    @Column(name = "motivo_moderacao", columnDefinition = "TEXT")
    private String motivoModeracao;
}
