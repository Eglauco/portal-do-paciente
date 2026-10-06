-- Widget Embedded da Clicksign usa autenticação por TOKEN (não embedded_signature). Método configurável
-- pelo cliente (SMS padrão; email/whatsapp também); a tela do Clicksign conduz a verificação e mostra o PDF.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
  ('Clicksign — Autenticação', 'Autenticação do signatário no Widget Embedded: sms (padrão), email ou whatsapp.', 'CLICKSIGN_AUTENTICACAO', 'TEXTO', 'sms');
