-- Pendência de assinatura de termo cuja origem é um MODELO do ZapSign não tem arquivo (.docx) nosso:
-- o snapshot da URL passa a ser opcional (a assinatura usa o modelo do provedor, não esta URL).
ALTER TABLE termo_assinatura
    ALTER COLUMN url DROP NOT NULL;
