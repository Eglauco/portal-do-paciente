-- Perfil da unidade (estilo "perfil de empresa" do WhatsApp): logo, capa, sobre, telefone, endereço
-- e horário de funcionamento por dia. Exibido no app ao tocar no nome da unidade dentro da conversa.
-- Aditivo/idempotente; aplicado em todos os inquilinos no boot (flywayMigrationStrategy).

ALTER TABLE unidade ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE unidade ADD COLUMN capa_url VARCHAR(500);
ALTER TABLE unidade ADD COLUMN sobre    TEXT;
ALTER TABLE unidade ADD COLUMN telefone VARCHAR(40);
ALTER TABLE unidade ADD COLUMN endereco TEXT;

-- Horário de funcionamento: uma linha por dia da semana (abre/fecha, ou fechado).
CREATE TABLE unidade_horario (
    id         BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL REFERENCES unidade (id) ON DELETE CASCADE,
    dia_semana VARCHAR(10) NOT NULL,
    fechado    BOOLEAN NOT NULL DEFAULT FALSE,
    abertura   TIME,
    fechamento TIME,
    CONSTRAINT uq_unidade_horario_dia UNIQUE (unidade_id, dia_semana)
);
