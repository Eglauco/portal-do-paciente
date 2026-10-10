-- Config de PLATAFORMA (Design B): defaults de identidade (cor, logomarca, imagem de fundo e frases do
-- login) gerenciados pelo SUPER-ADMIN. Vive SEMPRE no schema public (tabela de plataforma, igual
-- inquilino/usuario_login) — NUNCA dentro de um schema de inquilino. Por isso a migration fica em
-- db/platform (roda só no app principal/public), não em db/migration (que roda por-tenant).
--
-- Serve o PRE-LOGIN (/login, sem inquilino resolvido) e é o FALLBACK por-campo quando um inquilino
-- logado não cadastrou a própria cor/logo/etc. As chaves são as MESMAS do white-label por-inquilino
-- (ChaveConfiguracao) e os valores default são os mesmos hardcoded de hoje — nada muda visualmente.
--
-- Diferença p/ a tabela `configuracao` (por-inquilino): aqui NÃO há FK atualizado_por -> usuario (o
-- editor é o super-admin, e o public não tem a tabela usuario) e só há as colunas de valor usadas.
CREATE TABLE IF NOT EXISTS public.configuracao_plataforma (
    id             BIGSERIAL    PRIMARY KEY,
    chave          VARCHAR(100) NOT NULL,
    valor_cor      VARCHAR(9),
    valor_texto    TEXT,
    valor_imagem   TEXT,
    atualizado_em  TIMESTAMP,
    atualizado_por BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_configuracao_plataforma_chave ON public.configuracao_plataforma (chave);

-- Semeia as 6 chaves com os defaults de hoje (idempotente — não duplica em re-execução).
INSERT INTO public.configuracao_plataforma (chave, valor_cor)
SELECT 'COR_PRIMARIA_PLATAFORMA', '#0E8C7F'
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'COR_PRIMARIA_PLATAFORMA');

INSERT INTO public.configuracao_plataforma (chave, valor_texto)
SELECT 'NOME_PLATAFORMA', 'Portal do Paciente'
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'NOME_PLATAFORMA');

INSERT INTO public.configuracao_plataforma (chave, valor_texto)
SELECT 'LOGIN_TITULO', 'A gestão do cuidado começa aqui.'
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'LOGIN_TITULO');

INSERT INTO public.configuracao_plataforma (chave, valor_texto)
SELECT 'LOGIN_SUBTITULO',
       'Cadastre e acompanhe exames, consultas e informações dos pacientes — com segurança e agilidade no dia a dia da equipe.'
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'LOGIN_SUBTITULO');

INSERT INTO public.configuracao_plataforma (chave, valor_imagem)
SELECT 'LOGO_PLATAFORMA', NULL
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'LOGO_PLATAFORMA');

INSERT INTO public.configuracao_plataforma (chave, valor_imagem)
SELECT 'LOGIN_FUNDO', NULL
WHERE NOT EXISTS (SELECT 1 FROM public.configuracao_plataforma WHERE chave = 'LOGIN_FUNDO');
