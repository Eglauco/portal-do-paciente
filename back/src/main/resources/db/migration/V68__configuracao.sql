-- Configurações do sistema no padrão "chave → valor tipado". A regra de negócio busca
-- pela `chave` única e usa o campo de valor do `tipo_configuracao`. Cada configuração é
-- criada por uma migration de INSERT própria (quando a regra precisar dela); o admin só
-- ajusta o VALOR pela tela depois. Esta migration cria apenas a tabela (sem registros).
CREATE TABLE configuracao (
    id                BIGSERIAL PRIMARY KEY,
    nome              VARCHAR(120) NOT NULL,
    descricao         VARCHAR(400),
    chave             VARCHAR(100) NOT NULL UNIQUE,
    tipo_configuracao VARCHAR(20)  NOT NULL,
    valor_booleano    BOOLEAN,
    valor_texto       TEXT,
    valor_numerico    NUMERIC(19, 4),
    atualizado_em     TIMESTAMP,
    -- Quem fez a última alteração (auditoria). Se o usuário for removido, mantém a config.
    atualizado_por    BIGINT REFERENCES usuario (id) ON DELETE SET NULL
);
