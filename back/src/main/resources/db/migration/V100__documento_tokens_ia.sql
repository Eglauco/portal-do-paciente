-- Tokens gastos pela IA (Claude) na análise de cada documento: entrada (documento + prompts) e
-- saída (resumo). Nulos enquanto não analisado / quando não houve chamada à IA. Só para o back-office.
ALTER TABLE documento ADD COLUMN tokens_entrada BIGINT;
ALTER TABLE documento ADD COLUMN tokens_saida   BIGINT;
