-- Normalização: o nome do atendente/responsável deixa de ser copiado e passa a
-- vir por JOIN em `usuario` (via responsavel_id / usuario_id). Remove as colunas
-- *_nome duplicadas e cria as FKs que faltavam. As FKs usam ON DELETE padrão
-- (NO ACTION = RESTRICT): não é possível excluir um usuário referenciado — igual
-- às demais FKs para `usuario` (comentario, manifestacao_mensagem).

-- 1) Zera referências órfãs (lixo de execuções de teste: ids de usuário que não
--    existem mais), senão a criação da FK falharia.
UPDATE chat SET responsavel_id = NULL
 WHERE responsavel_id IS NOT NULL
   AND responsavel_id NOT IN (SELECT id FROM usuario);
UPDATE mensagem SET usuario_id = NULL
 WHERE usuario_id IS NOT NULL
   AND usuario_id NOT IN (SELECT id FROM usuario);

-- 2) FKs que faltavam (manifestacao_mensagem.usuario_id já possui a sua).
ALTER TABLE chat
  ADD CONSTRAINT fk_chat_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuario (id);
ALTER TABLE mensagem
  ADD CONSTRAINT fk_mensagem_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id);

-- 3) Remove as colunas *_nome (o nome agora vem da relação com `usuario`).
ALTER TABLE chat DROP COLUMN responsavel_nome;
ALTER TABLE mensagem DROP COLUMN usuario_nome;
ALTER TABLE manifestacao_mensagem DROP COLUMN usuario_nome;
