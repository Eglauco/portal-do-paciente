-- Rede social: postagens das unidades, curtidas (por aparelho) e comentários.

CREATE TABLE IF NOT EXISTS postagem (
    id                     BIGSERIAL PRIMARY KEY,
    titulo                 VARCHAR(160) NOT NULL,
    descricao              TEXT,
    mostrar_total_curtidas BOOLEAN NOT NULL DEFAULT TRUE,
    habilitar_comentarios  BOOLEAN NOT NULL DEFAULT TRUE,
    unidade_id             BIGINT NOT NULL REFERENCES unidade (id),
    url                    TEXT NOT NULL,
    criado_em              TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS curtida (
    id             BIGSERIAL PRIMARY KEY,
    postagem_id    BIGINT NOT NULL REFERENCES postagem (id) ON DELETE CASCADE,
    dispositivo_id VARCHAR(80) NOT NULL,
    criado_em      TIMESTAMP NOT NULL,
    CONSTRAINT uk_curtida_postagem_dispositivo UNIQUE (postagem_id, dispositivo_id)
);
CREATE INDEX idx_curtida_postagem ON curtida (postagem_id);

CREATE TABLE IF NOT EXISTS comentario (
    id          BIGSERIAL PRIMARY KEY,
    postagem_id BIGINT NOT NULL REFERENCES postagem (id) ON DELETE CASCADE,
    autor       VARCHAR(80) NOT NULL,
    texto       TEXT NOT NULL,
    criado_em   TIMESTAMP NOT NULL
);
CREATE INDEX idx_comentario_postagem ON comentario (postagem_id);
