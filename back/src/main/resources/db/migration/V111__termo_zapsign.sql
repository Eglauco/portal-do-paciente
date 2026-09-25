-- Integração ZapSign nos termos: token do Modelo (template) por termo do procedimento (registrado no
-- upload do .docx), e os campos do documento/assinatura por pendência (preenchidos ao iniciar/assinar).
ALTER TABLE termo_procedimento ADD COLUMN IF NOT EXISTS zapsign_template_token VARCHAR(120);

ALTER TABLE termo_assinatura ADD COLUMN IF NOT EXISTS zapsign_doc_token VARCHAR(120);
ALTER TABLE termo_assinatura ADD COLUMN IF NOT EXISTS signer_token      VARCHAR(120);
ALTER TABLE termo_assinatura ADD COLUMN IF NOT EXISTS signed_url        TEXT;
ALTER TABLE termo_assinatura ADD COLUMN IF NOT EXISTS assinado_em       TIMESTAMP;
