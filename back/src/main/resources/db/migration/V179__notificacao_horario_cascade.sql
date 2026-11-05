-- Vincula a notificação do paciente ao HORÁRIO que ela referencia. As notificações de AGENDAMENTO, FALTA e
-- LEMBRETE já guardam o id do horário em `referencia_id` (navegação do app), mas não havia vínculo no banco —
-- ao excluir o horário, a notificação ficava órfã e, ao tocar, não levava a lugar nenhum. Com o FK
-- ON DELETE CASCADE, excluir um horário (manual, pela exclusão da agenda ou pelo SIRESP) apaga automaticamente
-- as notificações dele. Decisão do dono (04/11/2026). Aditiva; aplica em todos os inquilinos no boot.
ALTER TABLE notificacao ADD COLUMN horario_id BIGINT;
ALTER TABLE notificacao ADD CONSTRAINT fk_notificacao_horario
    FOREIGN KEY (horario_id) REFERENCES horario (id) ON DELETE CASCADE;
-- Índice da FK: o Postgres NÃO indexa coluna de FK sozinho, e o ON DELETE CASCADE varre a filha por horario_id a
-- cada horário excluído. Sem índice, seria um seq scan da tabela (append-only, grande) por exclusão. Mesma
-- convenção dos irmãos em cascata (agendamento_entrega/agendamento_log/lembrete_disparo indexam o horario_id).
CREATE INDEX idx_notificacao_horario ON notificacao (horario_id);

-- Backfill: liga as notificações existentes desses tipos ao seu horário (quando o horário ainda existe).
UPDATE notificacao SET horario_id = referencia_id
 WHERE tipo IN ('AGENDAMENTO', 'FALTA', 'LEMBRETE')
   AND referencia_id IS NOT NULL
   AND referencia_id IN (SELECT id FROM horario);

-- Limpa as notificações desses tipos cujo horário JÁ não existe (já estavam "levando a nada").
DELETE FROM notificacao
 WHERE tipo IN ('AGENDAMENTO', 'FALTA', 'LEMBRETE')
   AND referencia_id IS NOT NULL
   AND referencia_id NOT IN (SELECT id FROM horario);
