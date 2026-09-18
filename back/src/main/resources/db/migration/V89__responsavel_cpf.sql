-- Login por CPF — Fase 1 (aditiva). O responsável ganha o próprio CPF, que passará a ser a
-- identidade de login do app (o telefone deixa de ser chave e vira só canal do OTP).
-- Nullable por ora: os cadastros de teste existentes serão preenchidos manualmente; o NOT NULL
-- entra no corte (Fase 2), junto com a troca do serviço. Nada de telefone é removido aqui.
--
-- O CPF do responsável NÃO é único global (a mesma pessoa pode ser responsável de vários
-- pacientes), mas não pode se repetir no mesmo paciente.
ALTER TABLE responsavel
    ADD COLUMN cpf VARCHAR(11);

CREATE UNIQUE INDEX uk_responsavel_paciente_cpf ON responsavel (paciente_id, cpf) WHERE cpf IS NOT NULL;
