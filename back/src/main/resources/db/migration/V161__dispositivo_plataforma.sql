-- Plataforma do aparelho ('android'/'ios'/'web'), enviada pelo app no registro (/dispositivo).
-- O PushService usa para escolher o shape do push de CHAT: iOS = notificacao visivel + mutableContent
-- (a Notification Service Extension confirma a entrega); Android = alem da notificacao visivel, um push
-- SILENCIOSO (data-only + _contentAvailable, alta prioridade) que acorda a task em background para
-- confirmar a entrega (2o "check") com o app FECHADO. Aditivo e idempotente; roda POR-TENANT (cada schema).
ALTER TABLE dispositivo ADD COLUMN IF NOT EXISTS plataforma varchar(16);
