-- Perfis de acesso (RBAC por tela) do back-office.
--   perfil          : nome do perfil
--   perfil_tela     : telas (itens de menu) que o perfil libera
--   perfil_unidade  : unidades de saúde que o perfil enxerga (1 ou várias)
--   usuario_perfil  : perfis de cada usuário (a permissão efetiva é a UNIÃO)
-- A liberação é por tela inteira. Sem "super admin": o acesso vem 100% dos perfis.

CREATE TABLE IF NOT EXISTS perfil (
    id        BIGSERIAL PRIMARY KEY,
    nome      VARCHAR(120) NOT NULL,
    criado_em TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS perfil_tela (
    perfil_id BIGINT      NOT NULL REFERENCES perfil (id) ON DELETE CASCADE,
    tela      VARCHAR(40) NOT NULL,
    PRIMARY KEY (perfil_id, tela)
);

CREATE TABLE IF NOT EXISTS perfil_unidade (
    perfil_id  BIGINT NOT NULL REFERENCES perfil (id)  ON DELETE CASCADE,
    unidade_id BIGINT NOT NULL REFERENCES unidade (id) ON DELETE CASCADE,
    PRIMARY KEY (perfil_id, unidade_id)
);

CREATE TABLE IF NOT EXISTS usuario_perfil (
    usuario_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    perfil_id  BIGINT NOT NULL REFERENCES perfil (id)  ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, perfil_id)
);

CREATE INDEX IF NOT EXISTS idx_usuario_perfil_perfil  ON usuario_perfil (perfil_id);
CREATE INDEX IF NOT EXISTS idx_perfil_unidade_unidade ON perfil_unidade (unidade_id);

-- Perfil "Administrador": todas as telas + todas as unidades, ligado a todos os
-- usuários existentes (migração dos acessos atuais para o novo modelo de perfis).
INSERT INTO perfil (nome, criado_em) VALUES ('Administrador', now());

INSERT INTO perfil_tela (perfil_id, tela)
SELECT p.id, t.tela
FROM perfil p
CROSS JOIN (VALUES
    ('DASHBOARD'), ('AGENDAMENTOS'), ('CHATS'), ('SAU'), ('TIPOS_MANIFESTACAO'),
    ('NPS'), ('CATEGORIAS_NPS'), ('PACIENTES'), ('PRONTUARIOS'), ('POSTAGENS'),
    ('ESPECIALIDADES'), ('PROFISSIONAIS'), ('PROCEDIMENTOS'), ('MOTIVOS_FALTA'),
    ('UNIDADES'), ('USUARIOS'), ('PERFIS'), ('CONFIGURACOES')
) AS t(tela)
WHERE p.nome = 'Administrador';

INSERT INTO perfil_unidade (perfil_id, unidade_id)
SELECT p.id, u.id
FROM perfil p CROSS JOIN unidade u
WHERE p.nome = 'Administrador';

INSERT INTO usuario_perfil (usuario_id, perfil_id)
SELECT us.id, p.id
FROM usuario us CROSS JOIN perfil p
WHERE p.nome = 'Administrador';
