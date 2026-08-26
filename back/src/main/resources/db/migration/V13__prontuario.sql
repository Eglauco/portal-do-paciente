-- Prontuário: vinculado a um agendamento, com número de atendimento único e
-- uma lista de documentos.

CREATE TABLE IF NOT EXISTS prontuario (
    id                 BIGSERIAL PRIMARY KEY,
    agendamento_id     BIGINT NOT NULL REFERENCES agendamento (id),
    numero_atendimento VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS documento (
    id             BIGSERIAL PRIMARY KEY,
    prontuario_id  BIGINT NOT NULL REFERENCES prontuario (id) ON DELETE CASCADE,
    nome           VARCHAR(160) NOT NULL
);

CREATE INDEX idx_documento_prontuario ON documento (prontuario_id);

-- ------------------------------------------------------------------
-- Dados de exemplo (prontuários de atendimentos com presença do paciente)
-- ------------------------------------------------------------------
INSERT INTO prontuario (id, agendamento_id, numero_atendimento) VALUES
    (1, 101, 'ATD-2026-0001'),
    (2, 103, 'ATD-2026-0002'),
    (3, 105, 'ATD-2026-0003');

SELECT setval(pg_get_serial_sequence('prontuario', 'id'), (SELECT MAX(id) FROM prontuario));

INSERT INTO documento (prontuario_id, nome) VALUES
    (1, 'Hemograma completo'),
    (1, 'Receita médica'),
    (1, 'Atestado de comparecimento'),
    (2, 'Laudo de raio-x'),
    (2, 'Receita médica'),
    (3, 'Solicitação de exames'),
    (3, 'Ficha de anamnese');
