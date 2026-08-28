package com.example.pop.postagem;

import java.time.LocalDateTime;

import com.example.pop.unidade.Unidade;

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

/** Postagem da "rede social" (feed) publicada por uma unidade de saúde. */
@Entity
@Table(name = "postagem")
@Getter
@Setter
@NoArgsConstructor
public class Postagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "mostrar_total_curtidas", nullable = false)
    private boolean mostrarTotalCurtidas = true;

    @Column(name = "habilitar_comentarios", nullable = false)
    private boolean habilitarComentarios = true;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;

    /** URL da imagem (4:5) no S3. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
