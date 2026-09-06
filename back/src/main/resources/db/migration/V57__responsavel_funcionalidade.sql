-- Permissões do responsável por funcionalidade do app: qual nível de acesso ele
-- tem em cada funcionalidade (Sem acesso / Só visualizar / Visualizar e lançar).
-- Só as concessões são gravadas — uma funcionalidade ausente = SEM_ACESSO (padrão).
-- Não há seed: responsáveis já existentes ficam sem registro (portanto sem acesso)
-- até o admin configurar. ON DELETE CASCADE: some junto com o responsável.

CREATE TABLE responsavel_funcionalidade (
    responsavel_id BIGINT NOT NULL,
    funcionalidade VARCHAR(30) NOT NULL,
    nivel          VARCHAR(20) NOT NULL,
    PRIMARY KEY (responsavel_id, funcionalidade),
    CONSTRAINT fk_responsavel_funcionalidade_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE CASCADE
);
