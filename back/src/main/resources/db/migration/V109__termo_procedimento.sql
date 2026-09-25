-- Documentos de Termo de Consentimento (TCLE) por procedimento: o arquivo Word (.docx/.doc) que a
-- unidade já mantém, guardado no S3 (pasta "tcle"). Um procedimento pode ter vários. A assinatura
-- eletrônica (ZapSign) consome esses documentos numa etapa posterior.
CREATE TABLE IF NOT EXISTS termo_procedimento (
    id              BIGSERIAL PRIMARY KEY,
    procedimento_id BIGINT NOT NULL REFERENCES procedimento(id) ON DELETE CASCADE,
    nome            VARCHAR(120) NOT NULL,
    url             TEXT NOT NULL,
    content_type    VARCHAR(120),
    criado_em       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_termo_procedimento_proc ON termo_procedimento(procedimento_id);
