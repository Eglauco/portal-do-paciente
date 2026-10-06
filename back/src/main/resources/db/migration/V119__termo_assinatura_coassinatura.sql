-- Coassinatura do profissional de saúde: 2º signatário (após o paciente) na pendência do termo.
ALTER TABLE termo_assinatura
    ADD COLUMN profissional_assina BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE termo_assinatura
    ADD COLUMN provider_signer_id_profissional VARCHAR(120);

ALTER TABLE termo_assinatura
    ADD COLUMN sign_url_profissional TEXT;

ALTER TABLE termo_assinatura
    ADD COLUMN assinado_em_profissional TIMESTAMP;
