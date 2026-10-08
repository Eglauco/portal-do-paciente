package com.example.pop.configuracaoagenda;

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

/**
 * Documento de Termo de Consentimento (TCLE) de um configuracaoAgenda: o arquivo Word (.docx/.doc)
 * que a unidade já mantém, guardado no S3 (pasta "tcle"). Um configuracaoAgenda pode ter vários.
 * A assinatura eletrônica (ZapSign) consome esses documentos numa etapa posterior.
 */
@Entity
@Table(name = "termo_configuracao_agenda")
@Getter
@Setter
@NoArgsConstructor
public class TermoConfiguracaoAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configuracao_agenda_id", nullable = false)
    private ConfiguracaoAgenda configuracaoAgenda;

    /** Nome do termo (ex.: "TCLE — Endoscopia digestiva alta"). */
    @Column(nullable = false, length = 120)
    private String nome;

    /**
     * Origem do modelo: {@code ARQUIVO} (.docx nosso no S3, serve qualquer provedor) ou {@code ZAPSIGN_MODELO}
     * (modelo já pronto no ZapSign, só o ZapSign assina). Ver {@link OrigemModeloTermo}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origem_modelo", nullable = false, length = 30)
    private OrigemModeloTermo origemModelo = OrigemModeloTermo.ARQUIVO;

    /** URL do arquivo Word no S3 (pasta "tcle"). Nulo quando a origem é um modelo do ZapSign. */
    @Column(columnDefinition = "TEXT")
    private String url;

    /** Content-Type do arquivo enviado (ex.: application/vnd.openxmlformats-officedocument.wordprocessingml.document). */
    @Column(name = "content_type", length = 120)
    private String contentType;

    /**
     * Token do MODELO (template) da ZapSign. Em {@code ARQUIVO}: registrado a partir do nosso .docx (cache,
     * sob demanda ao assinar). Em {@code ZAPSIGN_MODELO}: o token do modelo que o admin SELECIONOU no ZapSign.
     * Só a ZapSign usa; os provedores que renderizam local (Autentique/Clicksign/DocuSign) deixam isto nulo.
     */
    @Column(name = "provider_template_token", length = 120)
    private String providerTemplateToken;

    /** Nome do modelo do ZapSign selecionado (só exibição no back-office; em {@code ZAPSIGN_MODELO}). */
    @Column(name = "modelo_provider_nome", length = 200)
    private String modeloProviderNome;

    /**
     * Se TRUE, além do paciente, o PROFISSIONAL de saúde do atendimento também assina o termo (coassinatura,
     * depois do paciente). Ver [[assinatura-multi-provedor]] / coassinatura do profissional.
     */
    @Column(name = "profissional_assina", nullable = false)
    private boolean profissionalAssina = false;

    /**
     * Só vale com {@code profissionalAssina}=true: se TRUE, o profissional assina com CERTIFICADO DIGITAL
     * (ICP-Brasil, qualificada — ZapSign auth_mode certificadoDigital); se FALSE, assina em tela (avançada).
     */
    @Column(name = "profissional_certificado", nullable = false)
    private boolean profissionalCertificado = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
