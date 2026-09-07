-- Auditoria (LGPD) do cadastro do paciente: quem criou/alterou/inativou/reativou,
-- quando e QUAIS campos mudaram (antes/depois). Modelo em 2 níveis:
--   paciente_log            -> 1 linha por EVENTO (uma ação de salvar)
--   paciente_log_alteracao  -> 1 linha por CAMPO alterado naquele evento
--
-- Ator do back-office = usuario_id (atendente). Mantemos paciente_id/responsavel_id
-- para eventuais eventos originados no app no futuro. Só uma coluna de ator é
-- preenchida por linha, conforme `autor`.
--
-- FKs dos atores com ON DELETE SET NULL (o log sobrevive à remoção do ator, sem o
-- nome). O paciente NÃO é excluído (soft-delete via situacao); ainda assim, se um dia
-- for removido de fato (limpeza/erro), CASCADE apaga o histórico junto.

CREATE TABLE paciente_log (
    id              BIGSERIAL PRIMARY KEY,
    paciente_id     BIGINT NOT NULL,
    tipo            VARCHAR(20) NOT NULL,
    autor           VARCHAR(20) NOT NULL,
    paciente_ator_id BIGINT,
    responsavel_id  BIGINT,
    usuario_id      BIGINT,
    criado_em       TIMESTAMP NOT NULL,
    CONSTRAINT fk_paciente_log_paciente
        FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE CASCADE,
    CONSTRAINT fk_paciente_log_paciente_ator
        FOREIGN KEY (paciente_ator_id) REFERENCES paciente (id) ON DELETE SET NULL,
    CONSTRAINT fk_paciente_log_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL,
    CONSTRAINT fk_paciente_log_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL
);

CREATE INDEX idx_paciente_log_paciente ON paciente_log (paciente_id);

CREATE TABLE paciente_log_alteracao (
    id            BIGSERIAL PRIMARY KEY,
    log_id        BIGINT NOT NULL,
    campo         VARCHAR(40) NOT NULL,
    valor_antes   TEXT,
    valor_depois  TEXT,
    CONSTRAINT fk_paciente_log_alteracao_log
        FOREIGN KEY (log_id) REFERENCES paciente_log (id) ON DELETE CASCADE
);

CREATE INDEX idx_paciente_log_alteracao_log ON paciente_log_alteracao (log_id);
