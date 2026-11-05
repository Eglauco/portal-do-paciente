-- Nome social do paciente (Decreto nº 8.727/2016; Portaria MS nº 1.820/2009 — Carta dos Direitos
-- dos Usuários da Saúde): nome pelo qual o paciente deseja ser identificado, independentemente do
-- registro civil. O nome civil (coluna "nome") é PRESERVADO — só a exibição passa a usar o nome
-- social quando "utiliza_nome_social" = true. Aditivo/idempotente; aplicado em todos os inquilinos
-- no boot (flywayMigrationStrategy).

ALTER TABLE paciente ADD COLUMN nome_social         VARCHAR(120);
ALTER TABLE paciente ADD COLUMN utiliza_nome_social BOOLEAN NOT NULL DEFAULT FALSE;
