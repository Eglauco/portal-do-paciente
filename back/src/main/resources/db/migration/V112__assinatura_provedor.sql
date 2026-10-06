-- Abstração de provedor de assinatura eletrônica (ZapSign + Autentique).
-- Campos neutros (não presos a um provedor) + qual provedor criou cada termo + escolha do provedor.

-- termo_procedimento: token do modelo remoto (só a ZapSign usa; a Autentique renderiza local → NULL).
ALTER TABLE termo_procedimento RENAME COLUMN zapsign_template_token TO provider_template_token;

-- termo_assinatura: ids do documento/signatário no provedor (neutros) + qual provedor.
ALTER TABLE termo_assinatura RENAME COLUMN zapsign_doc_token TO provider_doc_token;
ALTER TABLE termo_assinatura RENAME COLUMN signer_token TO provider_signer_id;
ALTER TABLE termo_assinatura ADD COLUMN IF NOT EXISTS provedor VARCHAR(20) NOT NULL DEFAULT 'ZAPSIGN';

-- Configuração: escolha do provedor + modo de lote da Autentique.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
 ('Provedor de assinatura eletrônica',
  'Provedor usado para assinar os Termos de Consentimento (TCLE) no app. Opções: ZAPSIGN ou AUTENTIQUE. As credenciais de cada provedor ficam em variáveis de ambiente (não nesta tela).',
  'PROVEDOR_ASSINATURA', 'TEXTO', 'ZAPSIGN'),
 ('Autentique — modo de assinatura em lote',
  'Quando o provedor é AUTENTIQUE e um atendimento tem vários termos: SEPARADO = um documento/assinatura por termo (um PDF assinado por termo); COMBINADO = todos os termos num único PDF (uma assinatura só). Não afeta a ZapSign.',
  'AUTENTIQUE_MODO_LOTE', 'TEXTO', 'SEPARADO');
