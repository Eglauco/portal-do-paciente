-- Fase 2 da entrega: confirmar a entrega ao aparelho via RECEIPTS da Expo (assíncrono).
-- Guardamos os receipt ids "ok" do envio; um job consulta getReceipts e promove a linha de
-- NOTIFICACAO_ENVIADA para NOTIFICACAO_ENTREGUE (ou SEM_NOTIFICACAO_ATIVA se a entrega falhar).
-- Lista de receipt ids pendentes, separada por vírgula; NULL = nada a consultar.
ALTER TABLE agendamento_entrega ADD COLUMN receipts_pendentes TEXT;
