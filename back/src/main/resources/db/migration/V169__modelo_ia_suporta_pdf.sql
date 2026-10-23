-- Modelos de IA: marca se o modelo aceita PDF nativo como anexo. Usado para avisar o usuário quando o
-- MODELO — PRONTUÁRIO escolhido não suporta PDF (a análise de documento do prontuário manda o PDF nativo).
-- Default true: a maioria dos modelos de visão atuais (Claude, GPT-4o, Gemini) aceita PDF.
ALTER TABLE modelo_ia ADD COLUMN IF NOT EXISTS suporta_pdf BOOLEAN NOT NULL DEFAULT true;
