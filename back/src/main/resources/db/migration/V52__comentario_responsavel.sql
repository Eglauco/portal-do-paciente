-- Rede social: além de paciente_id (o perfil que fez o comentário), registra o
-- responsavel_id quando o comentário foi feito por um responsável representando
-- aquele paciente. Nulo quando o próprio paciente comentou.

ALTER TABLE comentario ADD COLUMN responsavel_id BIGINT;

ALTER TABLE comentario
    ADD CONSTRAINT fk_comentario_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL;

CREATE INDEX idx_comentario_responsavel ON comentario (responsavel_id);
