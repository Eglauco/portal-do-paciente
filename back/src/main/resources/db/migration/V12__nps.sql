-- NPS: pesquisa de satisfação vinculada a um agendamento (1:1).
-- Gerado automaticamente quando o agendamento registra PRESENCA_PACIENTE.

CREATE TABLE IF NOT EXISTS nps (
    id             BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL UNIQUE REFERENCES agendamento (id),
    status         VARCHAR(20) NOT NULL,
    nota           INTEGER,
    observacao     TEXT,
    criado_em      TIMESTAMP NOT NULL,
    respondido_em  TIMESTAMP,
    CONSTRAINT ck_nps_nota CHECK (nota IS NULL OR (nota BETWEEN 0 AND 10))
);

-- ------------------------------------------------------------------
-- Dados de exemplo
-- Atendimentos com PRESENCA_PACIENTE (ids altos para não colidir com a sequência).
-- Colunas: id, data_hora, especialidade_id, profissional_saude_id, procedimento_id,
--          paciente_id, unidade_id, status_agendamento
-- ------------------------------------------------------------------
INSERT INTO agendamento (id, data_hora, especialidade_id, profissional_saude_id, procedimento_id, paciente_id, unidade_id, status_agendamento) VALUES
    (101, '2026-08-20 09:00:00', 1, 1, 1, 1, 1, 'PRESENCA_PACIENTE'),
    (102, '2026-08-19 10:30:00', 2, 2, 6, 2, 1, 'PRESENCA_PACIENTE'),
    (103, '2026-08-18 14:00:00', 3, 3, 1, 3, 2, 'PRESENCA_PACIENTE'),
    (104, '2026-08-17 08:15:00', 5, 4, 2, 4, 1, 'PRESENCA_PACIENTE'),
    (105, '2026-08-16 16:45:00', 4, 5, 1, 5, 3, 'PRESENCA_PACIENTE'),
    (106, '2026-08-15 11:20:00', 6, 6, 4, 6, 1, 'PRESENCA_PACIENTE'),
    (107, '2026-08-14 13:10:00', 7, 7, 1, 7, 2, 'PRESENCA_PACIENTE'),
    (108, '2026-08-13 15:30:00', 8, 1, 5, 8, 1, 'PRESENCA_PACIENTE');

SELECT setval(pg_get_serial_sequence('agendamento', 'id'), (SELECT MAX(id) FROM agendamento));

INSERT INTO nps (agendamento_id, status, nota, observacao, criado_em, respondido_em) VALUES
    (101, 'RESPONDIDO', 10, 'Atendimento excelente, equipe muito atenciosa!', '2026-08-20 09:40:00', '2026-08-20 12:05:00'),
    (102, 'RESPONDIDO', 9,  'Rápido e organizado. Recomendo.',              '2026-08-19 11:10:00', '2026-08-19 18:22:00'),
    (103, 'RESPONDIDO', 8,  NULL,                                           '2026-08-18 14:50:00', '2026-08-19 09:00:00'),
    (104, 'RESPONDIDO', 7,  'Tudo certo, mas a espera foi um pouco longa.', '2026-08-17 09:00:00', '2026-08-17 20:15:00'),
    (105, 'RESPONDIDO', 5,  'Demorei bastante para ser atendido.',          '2026-08-16 17:30:00', '2026-08-18 10:40:00'),
    (106, 'RESPONDIDO', 3,  'Não fui bem informado sobre o preparo do exame.', '2026-08-15 12:00:00', '2026-08-16 08:30:00'),
    (107, 'PENDENTE',   NULL, NULL,                                         '2026-08-14 13:50:00', NULL),
    (108, 'EXPIRADO',   NULL, NULL,                                         '2026-08-13 16:10:00', NULL);

SELECT setval(pg_get_serial_sequence('nps', 'id'), (SELECT MAX(id) FROM nps));
