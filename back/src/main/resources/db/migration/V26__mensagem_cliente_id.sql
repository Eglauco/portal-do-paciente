-- Idempotência do envio: id gerado pelo cliente para evitar mensagem duplicada
-- quando o app/admin reenvia (rede lenta/caiu depois de o servidor já ter salvo).
ALTER TABLE mensagem ADD COLUMN cliente_id VARCHAR(60);

CREATE UNIQUE INDEX uk_mensagem_chat_cliente
    ON mensagem (chat_id, cliente_id)
    WHERE cliente_id IS NOT NULL;
