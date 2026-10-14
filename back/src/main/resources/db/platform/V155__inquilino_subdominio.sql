-- Multi-inquilino (Fase A — subdominio): rotulo DNS do inquilino, usado para resolver a clinica
-- ANTES do login (branding do back-office por subdominio: clinicaX.trilharsaude.app.br). Vive no
-- public (tabela de plataforma), junto de schema_name. Idempotente (lição do 1o deploy do Design B).
ALTER TABLE public.inquilino ADD COLUMN IF NOT EXISTS subdominio VARCHAR(63);

-- Indice unico PARCIAL: as linhas de plataforma (ex.: 'Plataforma'/'public', 'principal') podem
-- ficar com subdominio NULL; a unicidade so vale para os inquilinos que tem subdominio.
CREATE UNIQUE INDEX IF NOT EXISTS uk_inquilino_subdominio
    ON public.inquilino (subdominio) WHERE subdominio IS NOT NULL;
