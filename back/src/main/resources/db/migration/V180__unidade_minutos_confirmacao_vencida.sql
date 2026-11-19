-- Confirmação vencida: minutos APÓS a data/hora do horário em que um agendamento ainda
-- "Aguardando confirmação do paciente" passa a "Confirmação vencida" (rotina do back a cada 1 min).
-- Por unidade de saúde. 0 = regra desligada (padrão — cada unidade liga quando quiser).
-- O status novo (CONFIRMACAO_VENCIDA) não precisa de DDL: horario.status_agendamento é VARCHAR(40) sem CHECK.
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS minutos_confirmacao_vencida INTEGER NOT NULL DEFAULT 0;
