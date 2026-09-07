-- Moderação de comentários por IA (Claude). Flag por postagem + estado de moderação no
-- comentário. Só comentários NOVOS (após ligar o flag) passam pela validação; os existentes
-- ficam PUBLICADO. Comentário potencialmente ofensivo vira PENDENTE (oculto do público) até
-- o admin aprovar (PUBLICADO) ou rejeitar (REJEITADO).

ALTER TABLE postagem ADD COLUMN validar_comentarios_ia BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE comentario ADD COLUMN status_moderacao VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO';
ALTER TABLE comentario ADD COLUMN motivo_moderacao TEXT;

-- Índice para o admin listar rapidamente os comentários pendentes de uma postagem.
CREATE INDEX idx_comentario_status_moderacao ON comentario (postagem_id, status_moderacao);
