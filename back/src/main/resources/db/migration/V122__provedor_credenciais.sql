-- Credenciais dos provedores de assinatura geridas pelo cliente (self-service), em vez de variáveis de ambiente.
-- Os tokens/segredos ficam CIFRADOS (AES-GCM) na nova coluna valor_segredo (tipo de config SEGREDO, write-only).
-- Nascem VAZIOS: o cliente digita na tela "Provedores de assinatura". O AMBIENTE começa em SANDBOX.

ALTER TABLE configuracao ADD COLUMN valor_segredo TEXT;

-- Ambiente por provedor (TEXTO: SANDBOX | PRODUCAO) — define a base URL internamente.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
  ('ZapSign — Ambiente', 'Ambiente do ZapSign: SANDBOX ou PRODUCAO (define a base URL).', 'ZAPSIGN_AMBIENTE', 'TEXTO', 'SANDBOX'),
  ('Autentique — Ambiente', 'Ambiente da Autentique: SANDBOX ou PRODUCAO.', 'AUTENTIQUE_AMBIENTE', 'TEXTO', 'SANDBOX'),
  ('Clicksign — Ambiente', 'Ambiente da Clicksign: SANDBOX ou PRODUCAO (define a base URL e o env do widget).', 'CLICKSIGN_AMBIENTE', 'TEXTO', 'SANDBOX');

-- Tokens/segredos (SEGREDO) — vazios até o cliente preencher na tela de provedores.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao) VALUES
  ('ZapSign — Token de API', 'Token de API do ZapSign.', 'ZAPSIGN_API_TOKEN', 'SEGREDO'),
  ('ZapSign — Segredo do webhook', 'Segredo do webhook do ZapSign (header X-Zapsign-Secret, registrado no painel da ZapSign).', 'ZAPSIGN_WEBHOOK_SECRET', 'SEGREDO'),
  ('Autentique — Token de API', 'Token de API da Autentique.', 'AUTENTIQUE_API_TOKEN', 'SEGREDO'),
  ('Autentique — Segredo do webhook', 'Segredo do webhook da Autentique (valida o HMAC do X-Autentique-Signature).', 'AUTENTIQUE_WEBHOOK_SECRET', 'SEGREDO'),
  ('Clicksign — Access token', 'Access token da Clicksign.', 'CLICKSIGN_ACCESS_TOKEN', 'SEGREDO'),
  ('Clicksign — Segredo do webhook', 'Segredo do webhook da Clicksign (valida o HMAC do header Content-Hmac).', 'CLICKSIGN_WEBHOOK_SECRET', 'SEGREDO');
