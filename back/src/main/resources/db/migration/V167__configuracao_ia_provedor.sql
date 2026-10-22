-- IA multi-provedor: a credencial de cada escopo passa a ter um PROVEDOR (a unidade pode usar um
-- provedor e o inquilino outro). base_url opcional (Azure OpenAI / endpoint compatível / self-hosted).
-- As credenciais existentes ficam ANTHROPIC (mantém exatamente o comportamento atual). Aditiva/idempotente.
ALTER TABLE configuracao_ia_unidade ADD COLUMN IF NOT EXISTS provedor VARCHAR(30) NOT NULL DEFAULT 'ANTHROPIC';
ALTER TABLE configuracao_ia_unidade ADD COLUMN IF NOT EXISTS base_url TEXT;
