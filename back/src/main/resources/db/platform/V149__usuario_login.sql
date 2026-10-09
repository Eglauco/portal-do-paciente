-- Multi-inquilino: roteamento de login do ADMIN. public.usuario_login mapeia email -> inquilino,
-- para o /auth/login achar o schema do inquilino ANTES de autenticar (login em 2 fases). Vive
-- SEMPRE no public (tabela de plataforma); e-mail UNICO GLOBAL (1 pessoa = 1 inquilino no MVP).
CREATE TABLE IF NOT EXISTS public.usuario_login (
    id           BIGSERIAL    PRIMARY KEY,
    email        VARCHAR(160) NOT NULL,
    inquilino_id BIGINT       NOT NULL REFERENCES public.inquilino(id),
    criado_em    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuario_login_email ON public.usuario_login (lower(email));
