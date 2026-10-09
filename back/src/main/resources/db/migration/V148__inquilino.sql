-- Multi-inquilino (Fase 0.2): registro de inquilinos. Vive SEMPRE no schema public (tabela de
-- plataforma), NUNCA dentro de um schema de inquilino. Mapeia inquilino -> schema do Postgres.
-- Semeia o inquilino PADRAO (schema 'public'), que representa os dados que ja existem hoje.
CREATE TABLE IF NOT EXISTS public.inquilino (
    id            BIGSERIAL    PRIMARY KEY,
    nome          VARCHAR(160) NOT NULL,
    schema_name   VARCHAR(63)  NOT NULL,
    situacao      VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    criado_em     TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_inquilino_schema_name ON public.inquilino (schema_name);

-- Inquilino padrao: representa os dados que ja existem hoje (schema public). Idempotente.
INSERT INTO public.inquilino (nome, schema_name, situacao)
SELECT 'Plataforma', 'public', 'ATIVO'
WHERE NOT EXISTS (SELECT 1 FROM public.inquilino WHERE schema_name = 'public');
