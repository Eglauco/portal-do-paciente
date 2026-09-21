-- Custo dos modelos de IA (US$ por milhão de tokens / MTok) — configuráveis na tela de Configurações
-- (tipo NUMERICO). Valores iniciais conforme a tabela de preços vigente; o admin ajusta depois.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_numerico) VALUES
 ('Claude Opus 5 — Tokens de entrada (US$/MTok)',
  'Preço dos tokens de entrada (base) do Claude Opus 5, em dólares por milhão de tokens (MTok).',
  'CUSTO_IA_OPUS_5_ENTRADA_USD_MTOK', 'NUMERICO', 5),
 ('Claude Opus 5 — Tokens de saída (US$/MTok)',
  'Preço dos tokens de saída do Claude Opus 5, em dólares por milhão de tokens (MTok).',
  'CUSTO_IA_OPUS_5_SAIDA_USD_MTOK', 'NUMERICO', 25),
 ('Claude Haiku 4.5 — Tokens de entrada (US$/MTok)',
  'Preço dos tokens de entrada (base) do Claude Haiku 4.5, em dólares por milhão de tokens (MTok).',
  'CUSTO_IA_HAIKU_45_ENTRADA_USD_MTOK', 'NUMERICO', 1),
 ('Claude Haiku 4.5 — Tokens de saída (US$/MTok)',
  'Preço dos tokens de saída do Claude Haiku 4.5, em dólares por milhão de tokens (MTok).',
  'CUSTO_IA_HAIKU_45_SAIDA_USD_MTOK', 'NUMERICO', 5);

-- Custo (US$) por uso, congelado no momento da chamada (snapshot). 6 casas: custos por uso ficam
-- bem abaixo de 1 centavo (ex.: moderação ~US$ 0,0001). Nulo quando não houve chamada à IA.
ALTER TABLE documento  ADD COLUMN custo_usd NUMERIC(12, 6);
ALTER TABLE mensagem   ADD COLUMN custo_usd NUMERIC(12, 6);
ALTER TABLE comentario ADD COLUMN custo_usd NUMERIC(12, 6);
