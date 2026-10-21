-- Cadastro de Modelos de IA (Claude) + preco por modelo: fonte do select de modelos no cadastro da
-- credencial E do calculo de custo (preco por VERSAO exata). Substitui o preco por-familia (as 4
-- configs CUSTO_IA_*, que usavam substring opus/haiku e deixavam sonnet/outros sem preco).
-- Aditiva/idempotente; roda em todos os inquilinos no boot.

CREATE TABLE IF NOT EXISTS modelo_ia (
    id                     BIGSERIAL PRIMARY KEY,
    modelo_id              VARCHAR(80)   NOT NULL UNIQUE,
    nome                   VARCHAR(80)   NOT NULL,
    preco_entrada_usd_mtok NUMERIC(12,4) NOT NULL,
    preco_saida_usd_mtok   NUMERIC(12,4) NOT NULL,
    ativo                  BOOLEAN       NOT NULL DEFAULT TRUE
);

-- Seed dos modelos atuais do Claude com os precos oficiais (US$/MTok entrada/saida). Idempotente.
INSERT INTO modelo_ia (modelo_id, nome, preco_entrada_usd_mtok, preco_saida_usd_mtok, ativo) VALUES
  ('claude-fable-5-1',  'Claude Fable 5.1', 10.0000, 50.0000, TRUE),
  ('claude-opus-5-5',   'Claude Opus 5.5',   4.0000, 20.0000, TRUE),
  ('claude-opus-5',     'Claude Opus 5',     5.0000, 25.0000, TRUE),
  ('claude-sonnet-5-5', 'Claude Sonnet 5.5', 2.0000, 10.0000, TRUE),
  ('claude-sonnet-5',   'Claude Sonnet 5',   2.0000, 10.0000, TRUE),
  ('claude-haiku-5-5',  'Claude Haiku 5.5',  0.1000,  0.5000, TRUE),
  ('claude-haiku-4-5',  'Claude Haiku 4.5',  1.0000,  5.0000, TRUE)
ON CONFLICT (modelo_id) DO NOTHING;

-- Preco sai das Configuracoes (vira o cadastro acima): remove as 4 configs antigas por-familia.
DELETE FROM configuracao WHERE chave IN (
  'CUSTO_IA_OPUS_5_ENTRADA_USD_MTOK', 'CUSTO_IA_OPUS_5_SAIDA_USD_MTOK',
  'CUSTO_IA_HAIKU_45_ENTRADA_USD_MTOK', 'CUSTO_IA_HAIKU_45_SAIDA_USD_MTOK');

-- Modelo vira obrigatorio no cadastro da credencial: preenche os modelos em branco das credenciais
-- existentes com os padroes que estavam em uso (haiku p/ moderacao+chat, opus p/ prontuario).
UPDATE configuracao_ia_unidade SET
  modelo_moderacao  = COALESCE(modelo_moderacao,  'claude-haiku-4-5'),
  modelo_chat       = COALESCE(modelo_chat,       'claude-haiku-4-5'),
  modelo_prontuario = COALESCE(modelo_prontuario, 'claude-opus-5')
WHERE modelo_moderacao IS NULL OR modelo_chat IS NULL OR modelo_prontuario IS NULL;

-- Concede a nova tela MODELOS_IA a todo perfil que ja libera Configuracoes (uso imediato; o admin
-- ajusta depois na tela de Perfis). O enum Tela e VARCHAR livre — sem migration de schema.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT DISTINCT pt.perfil_id, 'MODELOS_IA'
FROM perfil_tela pt
WHERE pt.tela = 'CONFIGURACOES'
ON CONFLICT (perfil_id, tela) DO NOTHING;
