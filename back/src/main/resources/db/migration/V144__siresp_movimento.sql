-- Movimentação da mensagem do SIRESP (TIPO_CONSULTA/TIPO_EXAME = A/C/T): agendamento, cancelamento ou transferência.
-- Registros existentes são todos agendamentos (default). + os campos de "horário de origem" da transferência.
ALTER TABLE siresp ADD COLUMN tipo_movimento VARCHAR(20) NOT NULL DEFAULT 'AGENDAMENTO';
ALTER TABLE siresp ADD COLUMN id_age_consulta_hor_origem TEXT;
ALTER TABLE siresp ADD COLUMN id_age_exame_hor_origem    TEXT;
