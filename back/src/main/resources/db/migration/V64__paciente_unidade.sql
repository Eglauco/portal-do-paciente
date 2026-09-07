-- Vínculo N:N paciente ↔ unidade de saúde: define quais unidades o paciente pode ver e
-- com quais pode interagir (feed, notificações de feed, abrir chat/SAU). Regra estrita:
-- sem nenhuma unidade vinculada = sem acesso a essas funcionalidades.

CREATE TABLE paciente_unidade (
    paciente_id BIGINT NOT NULL,
    unidade_id  BIGINT NOT NULL,
    PRIMARY KEY (paciente_id, unidade_id),
    CONSTRAINT fk_paciente_unidade_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE CASCADE,
    CONSTRAINT fk_paciente_unidade_unidade  FOREIGN KEY (unidade_id)  REFERENCES unidade (id)  ON DELETE CASCADE
);
CREATE INDEX idx_paciente_unidade_unidade ON paciente_unidade (unidade_id);

-- Backfill (rollout estrito sem travar ninguém): vincula cada paciente às unidades que
-- ele JÁ usa — agendamentos, chats e manifestações do SAU. Pacientes sem histórico ficam
-- sem unidade (o admin atribui no cadastro).
INSERT INTO paciente_unidade (paciente_id, unidade_id)
SELECT DISTINCT paciente_id, unidade_id FROM agendamento  WHERE paciente_id IS NOT NULL AND unidade_id IS NOT NULL
UNION
SELECT DISTINCT paciente_id, unidade_id FROM chat         WHERE paciente_id IS NOT NULL AND unidade_id IS NOT NULL
UNION
SELECT DISTINCT paciente_id, unidade_id FROM manifestacao WHERE paciente_id IS NOT NULL AND unidade_id IS NOT NULL
ON CONFLICT DO NOTHING;
