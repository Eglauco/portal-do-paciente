-- Tabela de unidades de saúde.

CREATE TABLE IF NOT EXISTS unidade (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);

INSERT INTO unidade (nome) VALUES
    ('Unidade de Saúde 01'),
    ('Unidade de Saúde 02'),
    ('Unidade de Saúde 03'),
    ('UBS Jardim Primavera'),
    ('UBS Vila Nova'),
    ('Centro de Especialidades Médicas'),
    ('Policlínica Central'),
    ('Pronto Atendimento 24h');
