-- Expande o cadastro de Profissional de Saúde (conselho, dados pessoais, endereço,
-- contatos, foto, integração, ativo/inativação, especialidades e unidades).

ALTER TABLE profissional
    ADD COLUMN conselho_id       BIGINT REFERENCES conselho (id),
    ADD COLUMN numero_conselho   VARCHAR(40),
    ADD COLUMN sexo              VARCHAR(20),
    ADD COLUMN data_nascimento   DATE,
    ADD COLUMN rg                VARCHAR(20),
    ADD COLUMN cpf               VARCHAR(11),
    ADD COLUMN cns               VARCHAR(15),
    ADD COLUMN telefone          VARCHAR(20),
    ADD COLUMN rua               VARCHAR(160),
    ADD COLUMN numero            VARCHAR(20),
    ADD COLUMN bairro            VARCHAR(120),
    ADD COLUMN municipio         VARCHAR(120),
    ADD COLUMN uf                VARCHAR(2),
    ADD COLUMN cep               VARCHAR(8),
    ADD COLUMN complemento       VARCHAR(160),
    ADD COLUMN email             VARCHAR(160),
    ADD COLUMN foto_url          VARCHAR(512),
    ADD COLUMN codigo_integracao VARCHAR(60),
    ADD COLUMN ativo             BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN inativado_em      TIMESTAMP;

-- Únicos quando preenchidos (índices parciais permitem vários NULL).
CREATE UNIQUE INDEX uk_profissional_cpf ON profissional (cpf) WHERE cpf IS NOT NULL;
CREATE UNIQUE INDEX uk_profissional_cns ON profissional (cns) WHERE cns IS NOT NULL;
CREATE UNIQUE INDEX uk_profissional_codigo_integracao
    ON profissional (codigo_integracao) WHERE codigo_integracao IS NOT NULL;

-- Telefones adicionais.
CREATE TABLE profissional_telefone (
    profissional_id BIGINT NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    numero          VARCHAR(20)
);
CREATE INDEX idx_profissional_telefone ON profissional_telefone (profissional_id);

-- Especialidades que o profissional pode atender (N:N).
CREATE TABLE profissional_especialidade (
    profissional_id  BIGINT NOT NULL REFERENCES profissional (id)  ON DELETE CASCADE,
    especialidade_id BIGINT NOT NULL REFERENCES especialidade (id) ON DELETE CASCADE,
    PRIMARY KEY (profissional_id, especialidade_id)
);

-- Unidades de saúde em que o profissional atende (N:N).
CREATE TABLE profissional_unidade (
    profissional_id BIGINT NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    unidade_id      BIGINT NOT NULL REFERENCES unidade (id)      ON DELETE CASCADE,
    PRIMARY KEY (profissional_id, unidade_id)
);
