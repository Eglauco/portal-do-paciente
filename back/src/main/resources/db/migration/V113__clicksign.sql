-- Clicksign como 3º provedor de assinatura eletrônica.

-- Amplia a descrição do seletor de provedor para citar a Clicksign.
UPDATE configuracao
   SET descricao = 'Provedor usado para assinar os Termos de Consentimento no app. Opções: ZAPSIGN, AUTENTIQUE ou CLICKSIGN. As credenciais de cada provedor ficam em variáveis de ambiente (não nesta tela).'
 WHERE chave = 'PROVEDOR_ASSINATURA';

-- Modo de lote da Clicksign (SEPARADO = um documento por termo no envelope; COMBINADO = um PDF só).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
 ('Clicksign — modo de assinatura em lote',
  'Quando o provedor é CLICKSIGN e um atendimento tem vários termos: SEPARADO = um documento por termo (um PDF assinado por termo, na mesma cerimônia/envelope); COMBINADO = todos os termos num único PDF. Não afeta os outros provedores.',
  'CLICKSIGN_MODO_LOTE', 'TEXTO', 'SEPARADO');
