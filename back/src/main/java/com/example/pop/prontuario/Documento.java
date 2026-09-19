package com.example.pop.prontuario;

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

@Entity
@Table(name = "documento")
@Getter
@Setter
@NoArgsConstructor
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "prontuario_id", nullable = false)
    private Prontuario prontuario;

    @Column(nullable = false, length = 160)
    private String nome;

    /** URL do arquivo no S3 (opcional). */
    @Column(columnDefinition = "TEXT")
    private String url;

    // ---------- Análise por IA (ver StatusAnaliseDocumento) ----------

    /** Tipo do documento escolhido no upload; direciona a IA (prompts). Nulo = sem análise. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_id")
    private TipoDocumentoProntuario tipo;

    /** Resumo clínico gerado pela IA (só back-office). */
    @Column(name = "resumo_clinico", columnDefinition = "TEXT")
    private String resumoClinico;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_analise", nullable = false, length = 30)
    private StatusAnaliseDocumento statusAnalise = StatusAnaliseDocumento.NAO_ANALISADO;

    /** Usuário (atendente) que validou o alerta; nulo enquanto não validado. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "validado_por")
    private Usuario validadoPor;

    @Column(name = "validado_em")
    private LocalDateTime validadoEm;

    /** Quando a IA analisou o documento (nulo = ainda não analisado). */
    @Column(name = "analisado_em")
    private LocalDateTime analisadoEm;
}
