package com.example.pop.procedimento;

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

/**
 * Documento de Termo de Consentimento (TCLE) de um procedimento: o arquivo Word (.docx/.doc)
 * que a unidade já mantém, guardado no S3 (pasta "tcle"). Um procedimento pode ter vários.
 * A assinatura eletrônica (ZapSign) consome esses documentos numa etapa posterior.
 */
@Entity
@Table(name = "termo_procedimento")
@Getter
@Setter
@NoArgsConstructor
public class TermoProcedimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procedimento_id", nullable = false)
    private Procedimento procedimento;

    /** Nome do termo (ex.: "TCLE — Endoscopia digestiva alta"). */
    @Column(nullable = false, length = 120)
    private String nome;

    /** URL do arquivo Word no S3 (pasta "tcle"). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** Content-Type do arquivo enviado (ex.: application/vnd.openxmlformats-officedocument.wordprocessingml.document). */
    @Column(name = "content_type", length = 120)
    private String contentType;

    /**
     * Token do MODELO (template) registrado na ZapSign a partir deste Word (.docx). Preenchido no
     * upload; permite criar o documento de assinatura com substituição de variáveis. Nulo = não
     * registrado (ex.: .doc, ou falha no registro) → não assinável até re-registrar.
     */
    @Column(name = "zapsign_template_token", length = 120)
    private String zapsignTemplateToken;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
