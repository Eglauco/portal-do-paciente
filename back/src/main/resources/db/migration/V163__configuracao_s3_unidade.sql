-- S3 por escopo (custo/isolamento por unidade): cada unidade de saude configura seu proprio S3.
-- A linha com unidade_id NULL e o S3 PADRAO do inquilino (fotos de paciente/profissional, modelos de
-- TCLE, logomarca). O branding da PLATAFORMA (super-admin) segue no S3 global do application.properties.
-- A secret_key fica CIFRADA (SegredoCripto / app.secrets.key), como os segredos dos provedores de assinatura.
-- Aditiva/idempotente; roda em todos os inquilinos no boot (ProvisionamentoService).

CREATE TABLE IF NOT EXISTS configuracao_s3_unidade (
    id                BIGSERIAL PRIMARY KEY,
    unidade_id        BIGINT REFERENCES unidade (id) ON DELETE CASCADE,
    bucket            VARCHAR(200) NOT NULL,
    region            VARCHAR(60)  NOT NULL,
    public_endpoint   VARCHAR(300) NOT NULL,
    internal_endpoint VARCHAR(300) NOT NULL,
    access_key        VARCHAR(300) NOT NULL,
    secret_key        TEXT         NOT NULL,
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMP    NOT NULL,
    atualizado_em     TIMESTAMP    NOT NULL
);

-- No maximo UMA config por unidade; e no maximo UMA config padrao do inquilino (unidade_id NULL).
CREATE UNIQUE INDEX IF NOT EXISTS uk_s3_unidade
    ON configuracao_s3_unidade (unidade_id) WHERE unidade_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_s3_inquilino_padrao
    ON configuracao_s3_unidade ((TRUE)) WHERE unidade_id IS NULL;
