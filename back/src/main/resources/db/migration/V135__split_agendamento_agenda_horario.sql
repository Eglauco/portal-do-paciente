-- Divide "agendamento" em AGENDA (slot do profissional) + HORARIO (marcação do paciente).
-- Fase 1 = split 1:1 (1 agenda + 1 horário por agendamento), PRESERVANDO horario.id = agendamento.id para que as
-- tabelas que apontavam para agendamento só precisem repontar a FK (sem reescrever valores). A agenda recebe ids novos.

-- 1) AGENDA (slot): dia + profissional/especialidade/procedimento/unidade.
CREATE TABLE agenda (
    id                     BIGSERIAL PRIMARY KEY,
    data                   DATE   NOT NULL,
    profissional_saude_id  BIGINT NOT NULL,
    especialidade_id       BIGINT NOT NULL,
    procedimento_id        BIGINT NOT NULL,
    unidade_id             BIGINT NOT NULL,
    origem_agendamento_id  BIGINT,  -- temporária: mapeia o agendamento de origem para linkar o horário
    CONSTRAINT fk_agenda_profissional  FOREIGN KEY (profissional_saude_id) REFERENCES profissional (id),
    CONSTRAINT fk_agenda_especialidade FOREIGN KEY (especialidade_id)      REFERENCES especialidade (id),
    CONSTRAINT fk_agenda_procedimento  FOREIGN KEY (procedimento_id)       REFERENCES procedimento (id),
    CONSTRAINT fk_agenda_unidade       FOREIGN KEY (unidade_id)            REFERENCES unidade (id)
);
INSERT INTO agenda (data, profissional_saude_id, especialidade_id, procedimento_id, unidade_id, origem_agendamento_id)
SELECT CAST(data_hora AS date), profissional_saude_id, especialidade_id, procedimento_id, unidade_id, id
FROM agendamento;
CREATE INDEX idx_agenda_unidade_data ON agenda (unidade_id, data);

-- 2) HORARIO (marcação): id = agendamento.id; data_hora cheio (dia da agenda + hora de início); FK para agenda.
CREATE TABLE horario (
    id                   BIGSERIAL PRIMARY KEY,
    agenda_id            BIGINT      NOT NULL,
    data_hora            TIMESTAMP   NOT NULL,
    hora_fim             TIME,
    paciente_id          BIGINT      NOT NULL,
    status_agendamento   VARCHAR(40) NOT NULL,
    justificativa_falta  TEXT,
    falta_justificada_em TIMESTAMP,
    entrega_resumo       VARCHAR(30),
    CONSTRAINT fk_horario_agenda   FOREIGN KEY (agenda_id)   REFERENCES agenda (id),
    CONSTRAINT fk_horario_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id)
);
INSERT INTO horario (id, agenda_id, data_hora, paciente_id, status_agendamento, justificativa_falta, falta_justificada_em, entrega_resumo)
SELECT ag.id, a.id, ag.data_hora, ag.paciente_id, ag.status_agendamento, ag.justificativa_falta, ag.falta_justificada_em, ag.entrega_resumo
FROM agendamento ag
JOIN agenda a ON a.origem_agendamento_id = ag.id;
-- próxima id automática logo após os ids explícitos migrados (evita colisão em inserts futuros)
SELECT setval(pg_get_serial_sequence('horario', 'id'), COALESCE((SELECT MAX(id) FROM horario), 0) + 1, false);
CREATE INDEX idx_horario_agenda   ON horario (agenda_id);
CREATE INDEX idx_horario_paciente ON horario (paciente_id);

-- 3) Repontar as tabelas que apontavam para agendamento_id -> horario (valores idênticos: horario.id = agendamento.id).
--    Para cada uma: dropa a FK para agendamento (lookup robusto, cobre nomes auto-gerados), renomeia a coluna e
--    cria a FK para horario (preservando ON DELETE CASCADE onde havia).

-- 3a) motivos da falta (N:N) -> horario_motivo_falta
ALTER TABLE agendamento_motivo_falta RENAME TO horario_motivo_falta;
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'horario_motivo_falta'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE horario_motivo_falta DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE horario_motivo_falta RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE horario_motivo_falta ADD CONSTRAINT fk_horario_motivo_horario
    FOREIGN KEY (horario_id) REFERENCES horario (id) ON DELETE CASCADE;

-- 3b) histórico de status -> horario_log
ALTER TABLE agendamento_log RENAME TO horario_log;
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'horario_log'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE horario_log DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE horario_log RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE horario_log ADD CONSTRAINT fk_horario_log_horario
    FOREIGN KEY (horario_id) REFERENCES horario (id) ON DELETE CASCADE;

-- 3c) entrega da notificação -> horario_entrega
ALTER TABLE agendamento_entrega RENAME TO horario_entrega;
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'horario_entrega'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE horario_entrega DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE horario_entrega RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE horario_entrega ADD CONSTRAINT fk_horario_entrega_horario
    FOREIGN KEY (horario_id) REFERENCES horario (id) ON DELETE CASCADE;

-- 3d) NPS (1:1) — o UNIQUE acompanha o rename da coluna.
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'nps'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE nps DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE nps RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE nps ADD CONSTRAINT fk_nps_horario FOREIGN KEY (horario_id) REFERENCES horario (id);

-- 3e) Prontuário
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'prontuario'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE prontuario DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE prontuario RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE prontuario ADD CONSTRAINT fk_prontuario_horario FOREIGN KEY (horario_id) REFERENCES horario (id);

-- 3f) lembrete_disparo — o UNIQUE(lembrete_id, horario_id) acompanha o rename da coluna.
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint WHERE contype = 'f'
     AND conrelid = 'lembrete_disparo'::regclass AND confrelid = 'agendamento'::regclass;
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE lembrete_disparo DROP CONSTRAINT %I', c); END IF;
END $$;
ALTER TABLE lembrete_disparo RENAME COLUMN agendamento_id TO horario_id;
ALTER TABLE lembrete_disparo ADD CONSTRAINT fk_lembrete_disparo_horario
    FOREIGN KEY (horario_id) REFERENCES horario (id) ON DELETE CASCADE;

-- siresp.agendamento_id é coluna "solta" (sem FK) e o valor já aponta ao horário (mesmo id) — mantida como está.

-- 4) Remove o agendamento (nada mais referencia) e a coluna temporária de mapeamento.
DROP TABLE agendamento;
ALTER TABLE agenda DROP COLUMN origem_agendamento_id;
