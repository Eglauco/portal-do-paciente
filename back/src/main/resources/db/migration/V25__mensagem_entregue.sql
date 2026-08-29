-- Confirmação de ENTREGA da mensagem (não é "lido"): marca se a mensagem já
-- chegou no aparelho do destinatário (usado pelos "checks" estilo WhatsApp).
ALTER TABLE mensagem ADD COLUMN entregue BOOLEAN NOT NULL DEFAULT false;
