-- Tokens gastos pela assistente virtual (Claude) por mensagem gerada por IA no chat ao vivo:
-- entrada (system prompt + FAQ + ficha + histórico) e saída (a resposta). Nulos nas demais mensagens.
-- Só para o back-office; o app do paciente ignora.
ALTER TABLE mensagem ADD COLUMN tokens_entrada BIGINT;
ALTER TABLE mensagem ADD COLUMN tokens_saida   BIGINT;
