-- Conta do app: a "conta" autenticada pelo OTP é o TELEFONE (não o paciente).
-- É onde fica amarrado o aparelho ativo, para que um telefone que é só responsável
-- (não é paciente) também possa logar e escolher entre os perfis que acessa.
-- O perfil (paciente por quem se age) fica no token; a conta é o ponto de sessão.

CREATE TABLE conta_app (
    id                BIGSERIAL PRIMARY KEY,
    telefone          VARCHAR(20)  NOT NULL,
    dispositivo_ativo VARCHAR(120),
    criado_em         TIMESTAMP    NOT NULL,
    atualizado_em     TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX uk_conta_app_telefone ON conta_app (telefone);
