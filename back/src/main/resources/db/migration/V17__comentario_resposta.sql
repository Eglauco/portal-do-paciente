-- Respostas de comentários (threading em 1 nível, estilo Instagram).
-- Um comentário-raiz tem comentario_pai_id NULL; uma resposta aponta para o comentário-raiz.

ALTER TABLE comentario
    ADD COLUMN comentario_pai_id BIGINT REFERENCES comentario (id) ON DELETE CASCADE;

CREATE INDEX idx_comentario_pai ON comentario (comentario_pai_id);
