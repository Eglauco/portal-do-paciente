-- Avaliação do atendimento do SAU pelo paciente ao encerrar a manifestação:
-- 1 nota (1 a 5) + comentário opcional. avaliado_em != null significa "encerrada e
-- avaliada" (definitiva: não reabre mais). Enquanto fechada e SEM avaliação, o
-- paciente ainda pode reabrir respondendo.
ALTER TABLE manifestacao ADD COLUMN avaliacao_nota SMALLINT;
ALTER TABLE manifestacao ADD COLUMN avaliacao_comentario VARCHAR(500);
ALTER TABLE manifestacao ADD COLUMN avaliado_em TIMESTAMP;
