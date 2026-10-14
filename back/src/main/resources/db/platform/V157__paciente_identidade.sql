-- Fase B: credencial CENTRAL do paciente (identidade por CPF, no public). 1 CPF = 1 PIN que serve
-- TODAS as clinicas do paciente. A sessao/aparelho (conta_app.dispositivo_ativo) e o dado clinico
-- continuam POR-INQUILINO. Idempotente (padrao db/platform).
CREATE TABLE IF NOT EXISTS public.paciente_identidade (
    id               BIGSERIAL    PRIMARY KEY,
    cpf              VARCHAR(11)  NOT NULL,
    senha_hash       VARCHAR(100),                       -- PIN BCrypt; NULL = nao definido / resetado por OTP
    senha_tentativas INTEGER      NOT NULL DEFAULT 0,     -- erros de PIN consecutivos (lockout CENTRAL)
    bloqueado_ate    TIMESTAMP,                           -- lockout AUTO-EXPIRAVEL: bloqueado ate este instante (NULL = livre)
    criado_em        TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_paciente_identidade_cpf ON public.paciente_identidade (cpf);
