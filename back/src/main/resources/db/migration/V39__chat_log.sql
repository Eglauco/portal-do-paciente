-- Auditoria da conversa (Chat - Log): um registro por ação de atendente
-- (visualizar, assumir, transferir, resolver, reabrir) + mudanças de status.
-- Nomes vêm por JOIN (só os FKs), consistente com a normalização.
CREATE TABLE chat_log (
    id                 BIGSERIAL PRIMARY KEY,
    chat_id            BIGINT NOT NULL REFERENCES chat (id),
    tipo               VARCHAR(30) NOT NULL,
    usuario_id         BIGINT REFERENCES usuario (id),
    usuario_destino_id BIGINT REFERENCES usuario (id),
    status_anterior    VARCHAR(30),
    status_novo        VARCHAR(30),
    criado_em          TIMESTAMP NOT NULL
);

-- Leitura da linha do tempo por conversa.
CREATE INDEX idx_chat_log_chat ON chat_log (chat_id, criado_em);
-- FKs para usuario (o RESTRICT ao excluir usuário precisa varrer estas colunas).
CREATE INDEX idx_chat_log_usuario ON chat_log (usuario_id);
CREATE INDEX idx_chat_log_destino ON chat_log (usuario_destino_id);
