-- Opção "aberta 24 horas" por dia no horário de funcionamento da unidade: permite a unidade
-- SEMPRE aberta (todos os dias 24h) ou aberta 24h em alguns dias da semana. Quando true,
-- abertura/fechamento ficam nulos (o dia inteiro é aberto). Aditivo/idempotente; aplicado em
-- todos os inquilinos no boot (flywayMigrationStrategy).

ALTER TABLE unidade_horario ADD COLUMN vinte_quatro_horas BOOLEAN NOT NULL DEFAULT FALSE;
