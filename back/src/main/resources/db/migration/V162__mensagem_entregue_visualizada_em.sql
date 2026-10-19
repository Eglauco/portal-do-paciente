-- "Dados da mensagem" (estilo WhatsApp): data/hora de ENTREGUE e VISUALIZADA por mensagem.
-- Antes havia só o booleano `entregue` + o cursor do chat (`paciente_leu_em`); agora o back-office
-- mostra o momento exato de cada evento. Aditivo e idempotente (migra todos os inquilinos no boot).

ALTER TABLE mensagem ADD COLUMN IF NOT EXISTS entregue_em    TIMESTAMP;
ALTER TABLE mensagem ADD COLUMN IF NOT EXISTS visualizada_em TIMESTAMP;

-- Backfill aproximado dos dados já existentes (ambiente de teste):
-- entregue_em = enviada_em para mensagens da unidade já marcadas como entregues.
UPDATE mensagem
   SET entregue_em = enviada_em
 WHERE remetente = 'UNIDADE' AND entregue = true AND entregue_em IS NULL;

-- visualizada_em = cursor de leitura do chat, para as mensagens da unidade que o paciente já leu.
UPDATE mensagem m
   SET visualizada_em = c.paciente_leu_em
  FROM chat c
 WHERE m.chat_id = c.id
   AND m.remetente = 'UNIDADE'
   AND c.paciente_leu_em IS NOT NULL
   AND m.enviada_em <= c.paciente_leu_em
   AND m.visualizada_em IS NULL;
