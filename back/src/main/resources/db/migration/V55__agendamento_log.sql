-- Log de auditoria das trocas de status do agendamento: registra QUEM fez cada
-- mudança de status. O ator pode ser o próprio paciente, um responsável (agindo
-- pelo perfil dependente no app) ou a unidade (atendente do back-office).
--
-- Só uma das colunas de ator é preenchida por linha, conforme `autor`:
--   PACIENTE   -> paciente_id
--   RESPONSAVEL-> responsavel_id
--   UNIDADE    -> usuario_id (atendente)
-- ON DELETE SET NULL nos atores (o log sobrevive à remoção do ator, sem o nome);
-- ON DELETE CASCADE no agendamento (o histórico some junto com o agendamento).

CREATE TABLE agendamento_log (
    id              BIGSERIAL PRIMARY KEY,
    agendamento_id  BIGINT NOT NULL,
    autor           VARCHAR(20) NOT NULL,
    paciente_id     BIGINT,
    responsavel_id  BIGINT,
    usuario_id      BIGINT,
    status_anterior VARCHAR(40),
    status_novo     VARCHAR(40) NOT NULL,
    criado_em       TIMESTAMP NOT NULL,
    CONSTRAINT fk_agendamento_log_agendamento
        FOREIGN KEY (agendamento_id) REFERENCES agendamento (id) ON DELETE CASCADE,
    CONSTRAINT fk_agendamento_log_paciente
        FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE SET NULL,
    CONSTRAINT fk_agendamento_log_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL,
    CONSTRAINT fk_agendamento_log_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL
);

CREATE INDEX idx_agendamento_log_agendamento ON agendamento_log (agendamento_id);
CREATE INDEX idx_agendamento_log_responsavel ON agendamento_log (responsavel_id);
