-- Remove sobras do OTP antigo (antes do Twilio Verify). O backend não gera nem
-- guarda código de ativação — a verificação é delegada ao provedor —, então
-- estes campos nunca eram usados. A sessão do app vive em conta_app / paciente.dispositivo_ativo.

ALTER TABLE paciente DROP COLUMN codigo_ativacao_hash;
ALTER TABLE paciente DROP COLUMN codigo_ativacao_expira_em;
