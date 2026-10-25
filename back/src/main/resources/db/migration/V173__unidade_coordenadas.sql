-- Coordenadas do endereço da unidade (geocodificadas no salvar), para exibir o mapa nativo no app.
-- Nulas quando não há endereço ou a geocodificação falhou. Aditivo/idempotente; aplicado em todos
-- os inquilinos no boot (flywayMigrationStrategy).

ALTER TABLE unidade ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE unidade ADD COLUMN longitude DOUBLE PRECISION;
