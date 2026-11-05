-- Dados do paciente informados por LINHA na importação de agenda por Excel (um por horário).
-- Decisão de negócio: o paciente continua sendo LOCALIZADO pelo identificador antigo (id interno /
-- prontuário / código de integração); estes campos são apenas GRAVADOS JUNTO ao horário como os dados
-- que vieram na planilha (não criam nem alteram o cadastro do Paciente). "nome social" preenchido ⇒
-- utiliza_nome_social = true naquele registro. Nulos nas marcações manuais e nas importadas do SIRESP.
-- Aditiva/idempotente: o flywayMigrationStrategy aplica em todos os inquilinos no boot.
ALTER TABLE horario ADD COLUMN importacao_cpf VARCHAR(11);
ALTER TABLE horario ADD COLUMN importacao_nome VARCHAR(160);
ALTER TABLE horario ADD COLUMN importacao_nome_social VARCHAR(120);
ALTER TABLE horario ADD COLUMN importacao_utiliza_nome_social BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE horario ADD COLUMN importacao_telefone VARCHAR(20);
ALTER TABLE horario ADD COLUMN importacao_data_nascimento DATE;
