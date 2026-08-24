-- Tabela de pacientes.

CREATE TABLE IF NOT EXISTS paciente (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);

INSERT INTO paciente (nome) VALUES
    ('Mariana Duarte'),
    ('João Almeida'),
    ('Ana Beatriz Souza'),
    ('Carlos Mendes'),
    ('Fernanda Dias'),
    ('Lucas Ferreira'),
    ('Beatriz Ramalho'),
    ('Paulo Nogueira'),
    ('Helena Ribeiro'),
    ('Rafael Teixeira');
