-- Coassinatura do profissional com CERTIFICADO DIGITAL (ICP, qualificada) — configurável por termo.
ALTER TABLE termo_procedimento
    ADD COLUMN profissional_certificado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE termo_assinatura
    ADD COLUMN profissional_certificado BOOLEAN NOT NULL DEFAULT FALSE;
