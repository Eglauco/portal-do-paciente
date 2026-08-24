-- Migration inicial do Portal do Paciente (POP)
-- Cria a tabela base de controle de saúde do schema.

CREATE TABLE IF NOT EXISTS health_check (
    id          BIGSERIAL PRIMARY KEY,
    checked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    status      VARCHAR(20) NOT NULL DEFAULT 'OK'
);
