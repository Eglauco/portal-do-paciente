-- Tabela de profissionais de saúde.

CREATE TABLE IF NOT EXISTS profissional (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);

INSERT INTO profissional (nome) VALUES
    ('Dr. Rafael Lima'),
    ('Dra. Helena Costa'),
    ('Dra. Mariana Duarte'),
    ('Dr. Paulo Nunes'),
    ('Dr. Carlos Mendes'),
    ('Dra. Ana Beatriz'),
    ('Dr. Lucas Ferreira');
