-- Tabela de exames (clone de especialidade). Usada no lançamento automático do agendamento na importação do
-- SIRESP: o exame importado do CROSS é mapeado, por código de integração, para um procedimento.
--
-- codigo_integracao: código do exame em um sistema externo (CROSS), opcional e ÚNICO quando preenchido
-- (índice único PARCIAL permite múltiplos NULL, igual a uk_especialidade_codigo_integracao da V85).
-- procedimento_id: procedimento vinculado (opcional, NULL) — vira o procedimento do agendamento no SIRESP.
CREATE TABLE exame (
    id                BIGSERIAL PRIMARY KEY,
    nome              VARCHAR(120) NOT NULL,
    codigo_integracao VARCHAR(60),
    procedimento_id   BIGINT
);

ALTER TABLE exame ADD CONSTRAINT fk_exame_procedimento
    FOREIGN KEY (procedimento_id) REFERENCES procedimento (id);

CREATE UNIQUE INDEX uk_exame_codigo_integracao
    ON exame (codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;

-- Libera a tela EXAME para o perfil Administrador (semeado na V44). O admin ajusta os demais perfis na tela de Perfis.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT p.id, 'EXAME' FROM perfil p WHERE p.nome = 'Administrador'
ON CONFLICT (perfil_id, tela) DO NOTHING;
