-- Status + log do ENVIO do XML ao Sistema de Gestão (o "Post XML"), separado do processamento interno:
--   • status_envio: NAO_ENVIADO (padrão) / ENVIADO (Sistema de Gestão confirmou) / FALHA (tentou e não processou).
--   • log_envio:    resultado do último envio (separado do log_integracao, que volta a ser só o processamento interno).
--   • enviado_em:   data/hora da última tentativa de envio.
-- Registros antigos: ficam como NAO_ENVIADO e log_envio vazio (o histórico de envio antigo permanece no log_integracao).
ALTER TABLE siresp ADD COLUMN status_envio VARCHAR(20) NOT NULL DEFAULT 'NAO_ENVIADO';
ALTER TABLE siresp ADD COLUMN log_envio     TEXT;
ALTER TABLE siresp ADD COLUMN enviado_em    TIMESTAMP;
