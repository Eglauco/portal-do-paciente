-- Novo parâmetro: enviar o XML ao cliente automaticamente ao importar (replica o "Post XML" do SIRESP logo após
-- o upload). Começa LIGADO; só dispara de fato quando a URL de envio (SIRESP_POST_URL) está preenchida.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano) VALUES
  ('SIRESP — Enviar ao importar',
   'Reenvia o arquivo XML original ao cliente (Post XML) automaticamente após o upload. Requer a URL de envio preenchida.',
   'SIRESP_ENVIAR_AO_IMPORTAR', 'BOOLEANO', true);
