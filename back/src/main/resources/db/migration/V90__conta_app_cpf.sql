-- Login por CPF — Fase 1 (aditiva). A conta do app ganha o CPF que será sua nova identidade
-- (uma conta por CPF + aparelho, no lugar de uma conta por telefone).
-- Nullable por ora, com índice único parcial. No corte (Fase 2): as sessões atuais são zeradas
-- (todos relogam), o CPF vira NOT NULL, e a chave por telefone (uk_conta_app_telefone) + a
-- coluna telefone são removidas. Nada é removido nesta fase.
ALTER TABLE conta_app
    ADD COLUMN cpf VARCHAR(11);

CREATE UNIQUE INDEX uk_conta_app_cpf ON conta_app (cpf) WHERE cpf IS NOT NULL;
