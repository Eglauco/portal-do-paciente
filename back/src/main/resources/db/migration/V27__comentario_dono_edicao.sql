-- Dono do comentário (para o autor poder editar/excluir o próprio) e marca de edição.
-- Comentários antigos ficam com paciente_id nulo (sem dono) e, por isso, não são
-- editáveis nem excluíveis pelo app.
ALTER TABLE comentario ADD COLUMN paciente_id BIGINT REFERENCES paciente (id);
ALTER TABLE comentario ADD COLUMN editado_em TIMESTAMP;

CREATE INDEX idx_comentario_paciente ON comentario (paciente_id);
