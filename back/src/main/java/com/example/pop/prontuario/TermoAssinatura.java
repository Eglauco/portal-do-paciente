package com.example.pop.prontuario;

import java.time.LocalDateTime;

import com.example.pop.procedimento.TermoProcedimento;

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
 * Termo de Consentimento (TCLE) a assinar, gerado no prontuário quando o paciente registra presença
 * (se o procedimento tiver termos vinculados). É uma pendência de assinatura: guarda um snapshot do
 * nome/arquivo (congelado) e aponta para o {@link TermoProcedimento} de origem. A assinatura em si
 * (ZapSign) preenche os campos futuros. NÃO entra na coleção {@code documentos} do Prontuário — é
 * gerido pelo seu próprio repositório (evita o merge-por-URL/orphanRemoval do prontuário).
 */
@Entity
@Table(name = "termo_assinatura")
@Getter
@Setter
@NoArgsConstructor
public class TermoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prontuario_id", nullable = false)
    private Prontuario prontuario;

    /** Termo do procedimento que originou esta pendência (rastreio; nulo se a origem sumir). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termo_procedimento_id")
    private TermoProcedimento termoProcedimento;

    /** Snapshot do nome do termo no momento da geração. */
    @Column(nullable = false, length = 120)
    private String nome;

    /** Snapshot da URL do arquivo Word (S3) no momento da geração. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTermoAssinatura status = StatusTermoAssinatura.PENDENTE;

    /** Token do documento criado na ZapSign para esta assinatura (preenchido ao iniciar). */
    @Column(name = "zapsign_doc_token", length = 120)
    private String zapsignDocToken;

    /** Token do signatário na ZapSign (para conferência/rastreio). */
    @Column(name = "signer_token", length = 120)
    private String signerToken;

    /** URL (S3 do POP) do PDF assinado, gravado pelo webhook após a assinatura. */
    @Column(name = "signed_url", columnDefinition = "TEXT")
    private String signedUrl;

    @Column(name = "assinado_em")
    private LocalDateTime assinadoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
