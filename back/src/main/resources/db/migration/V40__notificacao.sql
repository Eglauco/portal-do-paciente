-- Notificações do paciente (tela "Notificações" do app). Gravadas sempre que o
-- backend dispara um push (menos chat). O nome do paciente/alvo é resolvido pelo
-- app; aqui guardamos título/corpo prontos + o tipo e o id de referência p/ navegar.
CREATE TABLE notificacao (
    id            BIGSERIAL PRIMARY KEY,
    paciente_id   BIGINT NOT NULL REFERENCES paciente (id) ON DELETE CASCADE,
    tipo          VARCHAR(30) NOT NULL,
    titulo        VARCHAR(120) NOT NULL,
    corpo         VARCHAR(400) NOT NULL,
    referencia_id BIGINT,
    lida          BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em     TIMESTAMP NOT NULL,
    lida_em       TIMESTAMP
);

-- Listagem por paciente (mais recentes primeiro) e contagem de não lidas (sino).
CREATE INDEX idx_notificacao_paciente ON notificacao (paciente_id, criado_em);
CREATE INDEX idx_notificacao_nao_lida ON notificacao (paciente_id, lida);
