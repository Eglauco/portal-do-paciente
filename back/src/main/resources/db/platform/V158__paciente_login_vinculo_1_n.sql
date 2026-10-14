-- Fase B: paciente_login deixa de ser CPF-unico-global (1 pessoa = 1 clinica) e vira VINCULO
-- identidade<->inquilino (1 CPF em N clinicas) — mesmo movimento que o V156 fez no admin
-- (usuario_login). Idempotente.
DROP INDEX IF EXISTS public.uk_paciente_login_cpf;

CREATE UNIQUE INDEX IF NOT EXISTS uk_paciente_login_cpf_inq
    ON public.paciente_login (cpf, inquilino_id);
