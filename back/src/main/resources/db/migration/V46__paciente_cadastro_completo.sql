-- Cadastro completo do paciente (back-office): dados pessoais, documentos e endereço.
-- Só o nome é obrigatório; os demais são opcionais. O telefone (coluna existente)
-- continua sendo o PRINCIPAL (chave do login no app); telefones adicionais ficam
-- na tabela paciente_telefone.

ALTER TABLE paciente
    ADD COLUMN codigo_integracao VARCHAR(60),
    ADD COLUMN prontuario        VARCHAR(60),
    ADD COLUMN sexo              VARCHAR(20),
    ADD COLUMN data_nascimento   DATE,
    ADD COLUMN rg                VARCHAR(20),
    ADD COLUMN cpf               VARCHAR(11),
    ADD COLUMN nome_mae          VARCHAR(120),
    ADD COLUMN nome_pai          VARCHAR(120),
    ADD COLUMN rua               VARCHAR(160),
    ADD COLUMN numero            VARCHAR(20),
    ADD COLUMN bairro            VARCHAR(120),
    ADD COLUMN municipio         VARCHAR(120),
    ADD COLUMN uf                VARCHAR(2),
    ADD COLUMN cep               VARCHAR(8),
    ADD COLUMN complemento       VARCHAR(160),
    ADD COLUMN email             VARCHAR(160),
    ADD COLUMN cns               VARCHAR(15);

-- Telefones adicionais (só dígitos), além do principal.
CREATE TABLE IF NOT EXISTS paciente_telefone (
    paciente_id BIGINT      NOT NULL REFERENCES paciente (id) ON DELETE CASCADE,
    numero      VARCHAR(20)
);
CREATE INDEX idx_paciente_telefone_paciente ON paciente_telefone (paciente_id);

-- Trava de duplicidade nos campos únicos (só quando preenchidos).
CREATE UNIQUE INDEX uk_paciente_cpf               ON paciente (cpf)               WHERE cpf IS NOT NULL;
CREATE UNIQUE INDEX uk_paciente_cns               ON paciente (cns)               WHERE cns IS NOT NULL;
CREATE UNIQUE INDEX uk_paciente_codigo_integracao ON paciente (codigo_integracao) WHERE codigo_integracao IS NOT NULL;
CREATE UNIQUE INDEX uk_paciente_prontuario        ON paciente (prontuario)        WHERE prontuario IS NOT NULL;
