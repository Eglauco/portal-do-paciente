-- Vínculo opcional Usuário → Profissional de saúde (o usuário "é" um profissional e pode assinar termos).
ALTER TABLE usuario
    ADD COLUMN profissional_saude_id BIGINT;

ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_profissional
    FOREIGN KEY (profissional_saude_id) REFERENCES profissional (id);

-- Um profissional pode estar vinculado a no máximo um usuário (no Postgres, UNIQUE permite vários NULL).
ALTER TABLE usuario
    ADD CONSTRAINT uq_usuario_profissional UNIQUE (profissional_saude_id);
