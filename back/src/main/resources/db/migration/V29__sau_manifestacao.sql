-- SAU (Serviço de Atendimento ao Usuário): manifestações do paciente (elogio/
-- crítica/sugestão) sobre uma unidade, com thread de mensagens paciente <-> SAU.

CREATE TABLE IF NOT EXISTS manifestacao (
    id            BIGSERIAL PRIMARY KEY,
    paciente_id   BIGINT NOT NULL REFERENCES paciente (id),
    unidade_id    BIGINT NOT NULL REFERENCES unidade (id),
    tipo          VARCHAR(20) NOT NULL,
    status        VARCHAR(30) NOT NULL,
    criado_em     TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE INDEX idx_manifestacao_paciente ON manifestacao (paciente_id);
CREATE INDEX idx_manifestacao_unidade ON manifestacao (unidade_id);

CREATE TABLE IF NOT EXISTS manifestacao_mensagem (
    id              BIGSERIAL PRIMARY KEY,
    manifestacao_id BIGINT NOT NULL REFERENCES manifestacao (id) ON DELETE CASCADE,
    autor           VARCHAR(20) NOT NULL,
    usuario_id      BIGINT REFERENCES usuario (id),
    usuario_nome    VARCHAR(160),
    texto           TEXT NOT NULL,
    criado_em       TIMESTAMP NOT NULL
);

CREATE INDEX idx_manifestacao_mensagem_manif ON manifestacao_mensagem (manifestacao_id);
