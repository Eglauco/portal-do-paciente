-- Multi-inquilino: roteamento de login do PACIENTE/responsável (por CPF). public.paciente_login
-- mapeia cpf -> inquilino, para o APP achar o schema ANTES de autenticar (OTP/PIN). Vive SEMPRE no
-- public; CPF UNICO GLOBAL (1 pessoa = 1 inquilino no MVP). O ponteiro nasce no cadastro do paciente.
CREATE TABLE IF NOT EXISTS public.paciente_login (
    id           BIGSERIAL    PRIMARY KEY,
    cpf          VARCHAR(11)  NOT NULL,
    inquilino_id BIGINT       NOT NULL REFERENCES public.inquilino(id),
    criado_em    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_paciente_login_cpf ON public.paciente_login (cpf);
