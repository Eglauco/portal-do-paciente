-- Dono do comentário quando é do admin (back-office), para o admin poder editar
-- o próprio comentário (mesma janela de 15 min). Comentários do paciente têm
-- usuario_id nulo; os do admin têm paciente_id nulo.
ALTER TABLE comentario ADD COLUMN usuario_id BIGINT REFERENCES usuario (id);

CREATE INDEX idx_comentario_usuario ON comentario (usuario_id);
