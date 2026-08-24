-- Tabela de especialidades.

CREATE TABLE IF NOT EXISTS especialidade (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);

INSERT INTO especialidade (nome) VALUES
    ('Cardiologia'),
    ('Dermatologia'),
    ('Ortopedia'),
    ('Oftalmologia'),
    ('Clínico Geral'),
    ('Ginecologia'),
    ('Pediatria'),
    ('Neurologia');
