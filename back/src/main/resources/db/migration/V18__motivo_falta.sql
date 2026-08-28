-- Motivo da falta do paciente (cadastro) + justificativa da falta no agendamento.

CREATE TABLE IF NOT EXISTS motivo_falta (
    id     BIGSERIAL PRIMARY KEY,
    motivo VARCHAR(120) NOT NULL,
    ativo  BOOLEAN NOT NULL DEFAULT TRUE
);

-- Motivos semeados para o paciente selecionar (um inativo para exercitar o filtro).
INSERT INTO motivo_falta (motivo, ativo) VALUES
    ('Esqueci do agendamento', TRUE),
    ('Problema de transporte', TRUE),
    ('Compromisso de trabalho', TRUE),
    ('Problema de saúde', TRUE),
    ('Motivo pessoal ou familiar', TRUE),
    ('Condições climáticas', TRUE),
    ('Não consegui remarcar a tempo', TRUE),
    ('Dificuldade financeira', FALSE);

-- Justificativa da falta preenchida pelo paciente no app.
ALTER TABLE agendamento
    ADD COLUMN justificativa_falta  TEXT,
    ADD COLUMN falta_justificada_em TIMESTAMP;

-- Motivos selecionados pelo paciente (N:N).
CREATE TABLE IF NOT EXISTS agendamento_motivo_falta (
    agendamento_id  BIGINT NOT NULL REFERENCES agendamento (id) ON DELETE CASCADE,
    motivo_falta_id BIGINT NOT NULL REFERENCES motivo_falta (id) ON DELETE CASCADE,
    PRIMARY KEY (agendamento_id, motivo_falta_id)
);
