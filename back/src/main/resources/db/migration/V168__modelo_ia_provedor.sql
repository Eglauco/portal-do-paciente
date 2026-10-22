-- Cada modelo do cadastro "Modelos de IA" passa a pertencer a um PROVEDOR (o select de modelo na
-- credencial filtra por ele). Os modelos Claude já semeados (V166) ficam ANTHROPIC. Aditiva/idempotente.
ALTER TABLE modelo_ia ADD COLUMN IF NOT EXISTS provedor VARCHAR(30) NOT NULL DEFAULT 'ANTHROPIC';
