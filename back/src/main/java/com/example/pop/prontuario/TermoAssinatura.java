package com.example.pop.prontuario;

import java.time.LocalDateTime;

import com.example.pop.assinatura.ProvedorAssinatura;
import com.example.pop.configuracaoagenda.TermoConfiguracaoAgenda;

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
 * (se o configuracaoAgenda tiver termos vinculados). É uma pendência de assinatura: guarda um snapshot do
 * nome/arquivo (congelado) e aponta para o {@link TermoConfiguracaoAgenda} de origem. A assinatura em si
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

    /** Termo do configuracaoAgenda que originou esta pendência (rastreio; nulo se a origem sumir). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termo_configuracao_agenda_id")
    private TermoConfiguracaoAgenda termoConfiguracaoAgenda;

    /** Snapshot do nome do termo no momento da geração. */
    @Column(nullable = false, length = 120)
    private String nome;

    /** Snapshot da URL do arquivo Word (S3) no momento da geração. Nulo quando a origem é modelo do ZapSign. */
    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusTermoAssinatura status = StatusTermoAssinatura.PENDENTE;

    /** Provedor que criou este documento (ZapSign/Autentique) — roteia webhook e download após troca. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvedorAssinatura provedor = ProvedorAssinatura.ZAPSIGN;

    /** Id do documento criado no PROVEDOR para esta assinatura (preenchido ao iniciar). */
    @Column(name = "provider_doc_token", length = 120)
    private String providerDocToken;

    /** Id do signatário no PROVEDOR (para conferência/rastreio). */
    @Column(name = "provider_signer_id", length = 120)
    private String providerSignerId;

    /** URL (S3 do POP) do PDF assinado, gravado pelo webhook após a assinatura. */
    @Column(name = "signed_url", columnDefinition = "TEXT")
    private String signedUrl;

    @Column(name = "assinado_em")
    private LocalDateTime assinadoEm;

    // ---------- Coassinatura do profissional de saúde (2º signatário, após o paciente) ----------

    /** Snapshot (na geração) se este termo exige a assinatura do profissional além do paciente. */
    @Column(name = "profissional_assina", nullable = false)
    private boolean profissionalAssina = false;

    /** Snapshot: profissional assina com certificado digital (ICP/qualificada) em vez de assinatura em tela. */
    @Column(name = "profissional_certificado", nullable = false)
    private boolean profissionalCertificado = false;

    /** Id do signatário PROFISSIONAL no provedor (add-signer), quando há coassinatura. */
    @Column(name = "provider_signer_id_profissional", length = 120)
    private String providerSignerIdProfissional;

    /** URL da cerimônia do PROFISSIONAL (usada na tela do profissional, embutida em iframe). */
    @Column(name = "sign_url_profissional", columnDefinition = "TEXT")
    private String signUrlProfissional;

    /** Momento em que o profissional coassinou. */
    @Column(name = "assinado_em_profissional")
    private LocalDateTime assinadoEmProfissional;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
