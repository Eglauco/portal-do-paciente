-- Notificações do back-office (sino do admin). Fan-out: uma linha por admin elegível
-- no momento do evento (quem tem a tela do tipo + acesso à unidade). O "lida" é por
-- administrador; o sino mostra só as da unidade ATIVA. Só eventos importantes/raros:
-- nova manifestação SAU, comentário pendente de moderação, NPS com nota baixa.
CREATE TABLE notificacao_admin (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuario (id)  ON DELETE CASCADE,
    unidade_id    BIGINT NOT NULL REFERENCES unidade (id)  ON DELETE CASCADE,
    tipo          VARCHAR(30)  NOT NULL,
    titulo        VARCHAR(120) NOT NULL,
    corpo         VARCHAR(400) NOT NULL,
    rota          VARCHAR(200) NOT NULL,
    referencia_id BIGINT,
    lida          BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em     TIMESTAMP NOT NULL,
    lida_em       TIMESTAMP
);

-- Listagem e contagem do sino (por admin + unidade ativa).
CREATE INDEX idx_notif_admin_lista    ON notificacao_admin (usuario_id, unidade_id, criado_em);
CREATE INDEX idx_notif_admin_nao_lida ON notificacao_admin (usuario_id, unidade_id, lida);
-- Dedup de não-lidas do mesmo evento.
CREATE INDEX idx_notif_admin_dedup    ON notificacao_admin (tipo, referencia_id, lida);
