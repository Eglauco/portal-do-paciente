-- Tokens gastos pela IA (Claude) ao moderar cada comentário da rede social: entrada (prompt +
-- comentário) e saída (a decisão). Nulos quando não houve moderação por IA. Só para o back-office.
ALTER TABLE comentario ADD COLUMN tokens_entrada BIGINT;
ALTER TABLE comentario ADD COLUMN tokens_saida   BIGINT;
