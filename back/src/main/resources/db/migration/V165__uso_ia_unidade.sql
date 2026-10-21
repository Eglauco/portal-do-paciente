-- Custo de IA detalhado POR UNIDADE: carimba no ledger a unidade que originou cada uso de IA
-- (chat/moderacao/prontuario). A tela "Uso de IA" passa a mostrar o gasto da unidade ativa do admin.
-- "Daqui pra frente": registros antigos ficam com unidade_id NULL. Aditiva/idempotente.

ALTER TABLE uso_ia ADD COLUMN IF NOT EXISTS unidade_id BIGINT;

CREATE INDEX IF NOT EXISTS ix_uso_ia_unidade ON uso_ia (unidade_id);
