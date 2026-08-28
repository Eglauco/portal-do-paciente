-- NPS por categoria: cadastro de categorias + nota por categoria + média no NPS.

CREATE TABLE IF NOT EXISTS categoria_nps (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Categorias semeadas (uma inativa para exercitar o filtro de ativas).
INSERT INTO categoria_nps (nome, ativo) VALUES
    ('Limpeza', TRUE),
    ('Atendimento médico', TRUE),
    ('Atendimento da recepção', TRUE),
    ('Estrutura e instalações', TRUE),
    ('Tempo de espera', TRUE),
    ('Estacionamento', FALSE);

-- Média das notas por categoria, guardada no NPS para a tela de pesquisa.
ALTER TABLE nps ADD COLUMN media DOUBLE PRECISION;

-- Nota (0 a 10) por categoria dentro de uma avaliação.
CREATE TABLE IF NOT EXISTS nps_categoria_nota (
    id               BIGSERIAL PRIMARY KEY,
    nps_id           BIGINT NOT NULL REFERENCES nps (id) ON DELETE CASCADE,
    categoria_nps_id BIGINT NOT NULL REFERENCES categoria_nps (id),
    nota             INTEGER NOT NULL,
    CONSTRAINT ck_nps_categoria_nota CHECK (nota BETWEEN 0 AND 10),
    CONSTRAINT uk_nps_categoria UNIQUE (nps_id, categoria_nps_id)
);
CREATE INDEX idx_nps_categoria_nota_nps ON nps_categoria_nota (nps_id);

-- Reinicia as respostas de NPS existentes para começar a nova rotina (por categoria) do zero.
UPDATE nps
   SET status = 'PENDENTE', nota = NULL, observacao = NULL, respondido_em = NULL, media = NULL
 WHERE status = 'RESPONDIDO';
