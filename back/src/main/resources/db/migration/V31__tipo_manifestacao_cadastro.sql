-- Transforma o "tipo de manifestação" (antes um enum fixo) num cadastro do admin.
-- Cada tipo tem nome, descrição de ajuda e o flag "ativo" (desativado some da
-- seleção do paciente, mas as manifestações já criadas continuam válidas).

CREATE TABLE tipo_manifestacao (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(80)  NOT NULL,
    descricao     VARCHAR(200),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMP    NOT NULL DEFAULT now()
);

-- Semeia os 3 tipos que existiam no enum (preserva o comportamento atual).
INSERT INTO tipo_manifestacao (nome, descricao) VALUES
    ('Elogio',   'Reconheça um bom atendimento.'),
    ('Crítica',  'Relate algo que não foi bom.'),
    ('Sugestão', 'Proponha uma melhoria.');

-- Passa manifestacao.tipo (enum string) para uma FK ao novo cadastro.
ALTER TABLE manifestacao ADD COLUMN tipo_id BIGINT REFERENCES tipo_manifestacao(id);

UPDATE manifestacao m SET tipo_id = tm.id
FROM tipo_manifestacao tm
WHERE tm.nome = CASE m.tipo
    WHEN 'ELOGIO'   THEN 'Elogio'
    WHEN 'CRITICA'  THEN 'Crítica'
    WHEN 'SUGESTAO' THEN 'Sugestão'
END;

ALTER TABLE manifestacao ALTER COLUMN tipo_id SET NOT NULL;
ALTER TABLE manifestacao DROP COLUMN tipo;

CREATE INDEX idx_manifestacao_tipo ON manifestacao (tipo_id);
