-- Responsáveis do paciente: cadastro PARALELO (não reaproveita a tabela paciente).
-- Cada responsável é um registro leve (nome + telefone) que pertence a um paciente.
-- Um paciente pode ter vários responsáveis; excluir o paciente remove os seus.

CREATE TABLE responsavel (
    id          BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT       NOT NULL REFERENCES paciente (id) ON DELETE CASCADE,
    nome        VARCHAR(120) NOT NULL,
    telefone    VARCHAR(20)
);

CREATE INDEX idx_responsavel_paciente ON responsavel (paciente_id);
