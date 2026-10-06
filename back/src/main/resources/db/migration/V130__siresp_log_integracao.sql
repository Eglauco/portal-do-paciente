-- Log de integração por registro do SIRESP: diagnóstico (texto) do que foi encontrado/não encontrado pelo
-- código de integração (especialidade, unidade, profissional, paciente) no momento da importação.
ALTER TABLE siresp ADD COLUMN log_integracao TEXT;
