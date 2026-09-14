-- Código de integração da especialidade (sistema externo), igual ao do paciente:
-- opcional e ÚNICO quando preenchido (índice único PARCIAL permite múltiplos NULL).
ALTER TABLE especialidade ADD COLUMN codigo_integracao VARCHAR(60);

CREATE UNIQUE INDEX uk_especialidade_codigo_integracao
    ON especialidade (codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;
