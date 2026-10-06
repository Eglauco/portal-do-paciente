-- Envio do XML ao cliente (replica o "Post XML" do SIRESP: HTTP POST, parâmetro "msg" com o XML inteiro).
-- 1) Guarda o XML bruto (auto-contido: NewDataSet + schema + a Mensagem) de cada registro na importação,
--    para reenviar fiel ao que o SIRESP mandaria. Registros antigos ficam NULL (o envio reconstrói do registro).
ALTER TABLE siresp ADD COLUMN xml_bruto TEXT;

-- 2) URL do cliente que recebe o XML via HTTP POST. Vazia = envio desabilitado (o botão fica bloqueado).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
  ('SIRESP — URL de envio (Post XML)',
   'Endpoint do cliente que recebe o XML via HTTP POST (parâmetro de formulário "msg"), replicando o Post XML do SIRESP. Deixe em branco para desabilitar o envio.',
   'SIRESP_POST_URL', 'TEXTO', NULL);
