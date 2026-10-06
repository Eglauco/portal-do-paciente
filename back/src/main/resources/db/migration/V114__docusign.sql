-- DocuSign como 4º provedor de assinatura eletrônica.

-- Amplia a descrição do seletor de provedor para citar o DocuSign.
UPDATE configuracao
   SET descricao = 'Provedor usado para assinar os Termos de Consentimento no app. Opções: ZAPSIGN, AUTENTIQUE, CLICKSIGN ou DOCUSIGN. As credenciais de cada provedor ficam em variáveis de ambiente (não nesta tela).'
 WHERE chave = 'PROVEDOR_ASSINATURA';

-- Modo de lote do DocuSign (SEPARADO = um documento por termo no envelope; COMBINADO = um PDF só).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
 ('DocuSign — modo de assinatura em lote',
  'Quando o provedor é DOCUSIGN e um atendimento tem vários termos: SEPARADO = um documento por termo (um PDF assinado por termo, na mesma cerimônia/envelope); COMBINADO = todos os termos num único PDF. Não afeta os outros provedores.',
  'DOCUSIGN_MODO_LOTE', 'TEXTO', 'SEPARADO');
