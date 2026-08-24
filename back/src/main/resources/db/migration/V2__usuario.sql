-- Tabela de usuários (contas de acesso da equipe administrativa).

CREATE TABLE IF NOT EXISTS usuario (
    id    BIGSERIAL PRIMARY KEY,
    nome  VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL
);

INSERT INTO usuario (nome, email) VALUES
    ('Administrador',        'adm@unidadesaude.com.br'),
    ('Ana Helena Costa',     'ana.costa@unidadesaude.com.br'),
    ('Rafael Lima',          'rafael.lima@unidadesaude.com.br'),
    ('Mariana Duarte',       'mariana.duarte@unidadesaude.com.br'),
    ('Paulo Nunes',          'paulo.nunes@unidadesaude.com.br'),
    ('Beatriz Almeida',      'beatriz.almeida@unidadesaude.com.br'),
    ('Carlos Mendes',        'carlos.mendes@unidadesaude.com.br'),
    ('Fernanda Dias',        'fernanda.dias@unidadesaude.com.br');
