package com.example.pop.storage;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Config de S3 por ESCOPO, dentro do schema do inquilino. {@code unidade_id} preenchido = S3 daquela
 * unidade de saúde; {@code unidade_id} NULL = S3 PADRÃO do inquilino (fotos, modelos de TCLE, logomarca).
 * O branding da plataforma (super-admin) não usa esta tabela — fica no S3 global do application.properties.
 * A {@code secret_key} é gravada CIFRADA ({@link com.example.pop.configuracao.SegredoCripto}); o
 * {@code access_key} fica em claro (é um identificador, como o access key ID da AWS).
 */
@Entity
@Table(name = "configuracao_s3_unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoS3 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unidade dona desta config; NULL = S3 PADRÃO do inquilino. */
    @Column(name = "unidade_id")
    private Long unidadeId;

    @Column(nullable = false, length = 200)
    private String bucket;

    @Column(nullable = false, length = 60)
    private String region;

    @Column(name = "public_endpoint", nullable = false, length = 300)
    private String publicEndpoint;

    @Column(name = "internal_endpoint", nullable = false, length = 300)
    private String internalEndpoint;

    @Column(name = "access_key", nullable = false, length = 300)
    private String accessKey;

    /** CIFRADA (SegredoCripto). Nunca exposta em claro pela API. */
    @Column(name = "secret_key", nullable = false, columnDefinition = "TEXT")
    private String secretKey;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
