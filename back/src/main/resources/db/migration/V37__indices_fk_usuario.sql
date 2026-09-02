-- Índices nas colunas filhas das FKs para `usuario`. O PostgreSQL NÃO indexa
-- coluna de FK automaticamente (só PK/UNIQUE). Sem eles:
--   * excluir um usuário (checagem RESTRICT) varre cada tabela que o referencia;
--   * o filtro por responsável na lista de chats varre `chat`.
-- Segue a convenção do projeto (idx_comentario_usuario, idx_mensagem_chat, ...).
CREATE INDEX idx_chat_responsavel ON chat (responsavel_id);
CREATE INDEX idx_mensagem_usuario ON mensagem (usuario_id);
-- FK pré-existente (V29) que também não tinha índice: completa a eficiência do
-- RESTRICT ao excluir um usuário que respondeu manifestações do SAU.
CREATE INDEX idx_manifestacao_mensagem_usuario ON manifestacao_mensagem (usuario_id);
