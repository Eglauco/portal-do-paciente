-- Credencial de IA (Claude/Anthropic) por escopo: o CLIENTE cadastra e gerencia a propria chave.
-- Linha com unidade_id NULL = PADRAO do inquilino (a unidade sem config herda dele). Nao ha nivel de
-- plataforma (e "nunca global"): sem credencial, cada frente de IA degrada como ja faz hoje
-- (moderacao->pendente, chat->escala humano, prontuario->indisponivel).
-- A api_key fica CIFRADA (SegredoCripto / app.secrets.key). Modelos por frente opcionais (em branco =
-- usa o padrao da plataforma). Aditiva/idempotente; roda em todos os inquilinos no boot.

CREATE TABLE IF NOT EXISTS configuracao_ia_unidade (
    id                BIGSERIAL PRIMARY KEY,
    unidade_id        BIGINT REFERENCES unidade (id) ON DELETE CASCADE,
    api_key           TEXT         NOT NULL,
    modelo_moderacao  VARCHAR(80),
    modelo_chat       VARCHAR(80),
    modelo_prontuario VARCHAR(80),
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMP    NOT NULL,
    atualizado_em     TIMESTAMP
);

-- No maximo UMA config por unidade; e no maximo UMA config padrao do inquilino (unidade_id NULL).
CREATE UNIQUE INDEX IF NOT EXISTS uk_ia_unidade
    ON configuracao_ia_unidade (unidade_id) WHERE unidade_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ia_inquilino_padrao
    ON configuracao_ia_unidade ((TRUE)) WHERE unidade_id IS NULL;
