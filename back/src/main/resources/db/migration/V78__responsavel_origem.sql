-- Origem do responsável: ADMIN (cadastrado no back-office) ou PACIENTE (adicionado pelo
-- próprio paciente no app, com escopo travado em AGENDAMENTOS). Registros existentes = ADMIN.
ALTER TABLE responsavel ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
