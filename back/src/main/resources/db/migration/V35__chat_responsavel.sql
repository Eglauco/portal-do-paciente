-- "Assumir conversa": o atendente responsável pela conversa (só ele pode enviar)
-- e a atribuição do atendente em cada mensagem da unidade (mostrada ao paciente).

ALTER TABLE chat ADD COLUMN responsavel_id BIGINT;
ALTER TABLE chat ADD COLUMN responsavel_nome VARCHAR(160);

ALTER TABLE mensagem ADD COLUMN usuario_id BIGINT;
ALTER TABLE mensagem ADD COLUMN usuario_nome VARCHAR(160);
