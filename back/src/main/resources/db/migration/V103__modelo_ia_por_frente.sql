-- Registra QUAL modelo de IA (ex.: claude-opus-5, claude-haiku-4-5) foi usado em cada frente que
-- consome IA, para permitir o cálculo de custo depois (preço varia por modelo). Nulo quando não
-- houve chamada à IA. Só para o back-office.
ALTER TABLE documento  ADD COLUMN modelo_ia VARCHAR(60);
ALTER TABLE mensagem   ADD COLUMN modelo_ia VARCHAR(60);
ALTER TABLE comentario ADD COLUMN modelo_ia VARCHAR(60);
