-- Histórico do horário passa a registrar também EVENTOS, além das trocas de status: o atendente
-- enviou o convite do app por SMS, ou abriu o convite no WhatsApp, a partir das telas de agenda/horário.
--
-- Linha de EVENTO: evento preenchido (CONVITE_SMS / CONVITE_WHATSAPP), detalhe = número MASCARADO
-- (ex.: "(18) •••••-2191"), status_anterior/status_novo nulos. Linha de STATUS: como sempre (evento nulo).
-- Aditiva e idempotente (DROP NOT NULL e ADD COLUMN IF NOT EXISTS podem rodar de novo sem erro).

ALTER TABLE horario_log ALTER COLUMN status_novo DROP NOT NULL;
ALTER TABLE horario_log ADD COLUMN IF NOT EXISTS evento VARCHAR(30);
ALTER TABLE horario_log ADD COLUMN IF NOT EXISTS detalhe VARCHAR(120);
