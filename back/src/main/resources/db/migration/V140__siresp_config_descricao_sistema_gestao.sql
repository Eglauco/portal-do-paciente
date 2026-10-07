-- Rename "Cliente" -> "Sistema de Gestão" nas DESCRIÇÕES das configs do SIRESP (ficam visíveis na tela genérica de
-- Configurações). As migrations V131/V133 que as semearam já foram aplicadas (checksum do Flyway) — por isso o ajuste
-- vem numa migration nova, via UPDATE, em vez de editar as originais.
UPDATE configuracao
   SET descricao = 'Endpoint do Sistema de Gestão da unidade que recebe o XML via HTTP POST (parâmetro de formulário "msg"), replicando o Post XML do SIRESP. Em branco, o envio fica desabilitado.'
 WHERE chave = 'SIRESP_POST_URL';

UPDATE configuracao
   SET descricao = 'Reenvia o arquivo XML original ao Sistema de Gestão (Post XML) automaticamente após o upload. Só dispara se houver URL configurada.'
 WHERE chave = 'SIRESP_ENVIAR_AO_IMPORTAR';
