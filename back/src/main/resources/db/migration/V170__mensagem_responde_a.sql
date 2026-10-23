-- Chat: "responder mensagem" (citação estilo WhatsApp). Cada mensagem pode referenciar OUTRA
-- mensagem do MESMO chat (a citada). Auto-FK nullable; ON DELETE SET NULL pra a exclusão da
-- mensagem citada não travar nem apagar a resposta (ela só perde a citação).
ALTER TABLE mensagem ADD COLUMN IF NOT EXISTS responde_a_id BIGINT;

ALTER TABLE mensagem DROP CONSTRAINT IF EXISTS fk_mensagem_responde_a;
ALTER TABLE mensagem ADD CONSTRAINT fk_mensagem_responde_a
    FOREIGN KEY (responde_a_id) REFERENCES mensagem(id) ON DELETE SET NULL;
