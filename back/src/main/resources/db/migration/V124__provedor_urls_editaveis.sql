-- URLs base dos provedores agora são EDITÁVEIS pelo cliente (o provedor pode mudar o link).
-- Cada provedor ganha "URL de sandbox" e "URL de produção" (TEXTO). O *_AMBIENTE continua sendo
-- o seletor de qual das duas está ativa. Semeia com as URLs que antes eram constantes no código.

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
  ('ZapSign — URL de sandbox', 'URL base da API do ZapSign em sandbox.', 'ZAPSIGN_URL_SANDBOX', 'TEXTO', 'https://sandbox.api.zapsign.com.br/api/v1'),
  ('ZapSign — URL de produção', 'URL base da API do ZapSign em produção.', 'ZAPSIGN_URL_PRODUCAO', 'TEXTO', 'https://api.zapsign.com.br/api/v1'),
  ('Autentique — URL de sandbox', 'Endpoint GraphQL da Autentique (o sandbox usa o mesmo endpoint, com a flag sandbox).', 'AUTENTIQUE_URL_SANDBOX', 'TEXTO', 'https://api.autentique.com.br/v2/graphql'),
  ('Autentique — URL de produção', 'Endpoint GraphQL da Autentique em produção.', 'AUTENTIQUE_URL_PRODUCAO', 'TEXTO', 'https://api.autentique.com.br/v2/graphql'),
  ('Clicksign — URL de sandbox', 'URL base da API v3 da Clicksign em sandbox (o host do widget deriva dela).', 'CLICKSIGN_URL_SANDBOX', 'TEXTO', 'https://sandbox.clicksign.com/api/v3'),
  ('Clicksign — URL de produção', 'URL base da API v3 da Clicksign em produção (o host do widget deriva dela).', 'CLICKSIGN_URL_PRODUCAO', 'TEXTO', 'https://app.clicksign.com/api/v3');
