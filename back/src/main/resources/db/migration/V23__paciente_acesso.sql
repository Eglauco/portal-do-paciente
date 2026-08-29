-- Acesso do paciente ao app: telefone (chave do login), liberação GLOBAL (ativo),
-- código de ativação (guardado só o hash, com validade) e o aparelho ativo
-- (uma sessão por vez — trocar de aparelho exige reativar).

ALTER TABLE paciente
    ADD COLUMN telefone                  VARCHAR(20),
    ADD COLUMN ativo                     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN codigo_ativacao_hash      VARCHAR(100),
    ADD COLUMN codigo_ativacao_expira_em TIMESTAMP,
    ADD COLUMN dispositivo_ativo         VARCHAR(120);

-- O telefone é a chave do login: único (ignora nulos).
CREATE UNIQUE INDEX uk_paciente_telefone ON paciente (telefone) WHERE telefone IS NOT NULL;

-- Telefones de exemplo (apenas dígitos) para facilitar o teste.
UPDATE paciente SET telefone = '11999990001' WHERE nome = 'Mariana Duarte';
UPDATE paciente SET telefone = '11999990002' WHERE nome = 'João Almeida';
