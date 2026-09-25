-- Termos de Consentimento (TCLE) a assinar, gerados no prontuário quando o paciente registra
-- presença (se o procedimento tiver termos vinculados). É uma pendência de assinatura, com snapshot
-- do nome/arquivo; aponta para o termo do procedimento de origem (nulo se a origem for removida).
CREATE TABLE IF NOT EXISTS termo_assinatura (
    id                    BIGSERIAL PRIMARY KEY,
    prontuario_id         BIGINT NOT NULL REFERENCES prontuario(id) ON DELETE CASCADE,
    termo_procedimento_id BIGINT REFERENCES termo_procedimento(id) ON DELETE SET NULL,
    nome                  VARCHAR(120) NOT NULL,
    url                   TEXT NOT NULL,
    content_type          VARCHAR(120),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criado_em             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_termo_assinatura_prontuario ON termo_assinatura(prontuario_id);
