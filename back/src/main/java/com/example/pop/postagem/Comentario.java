package com.example.pop.postagem;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false, length = 80)
    private String autor;

    /** Dono do comentário quando é do paciente (app). Nulo em comentários antigos ou do admin. */
    @Column(name = "paciente_id")
    private Long pacienteId;

    /** Dono do comentário quando é do admin (back-office). Nulo em comentários do paciente. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Quando foi editado pela última vez (nulo se nunca editado). */
    @Column(name = "editado_em")
    private LocalDateTime editadoEm;
}
