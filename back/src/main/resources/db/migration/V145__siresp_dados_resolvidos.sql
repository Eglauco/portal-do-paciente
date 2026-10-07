-- Marca que o sistema preencheu campos de exibição (paciente/data/especialidade/etc.) a partir do horário encontrado
-- no processamento — dados que NÃO vieram do XML (cancelamento/transferência têm XML enxuto). Avisa no detalhe + deixa
-- o registro pesquisável. Registros antigos ficam false.
ALTER TABLE siresp ADD COLUMN dados_resolvidos BOOLEAN NOT NULL DEFAULT false;
