-- Tabela de agendamentos, vinculada a paciente e unidade.

CREATE TABLE IF NOT EXISTS agendamento (
    id                 BIGSERIAL PRIMARY KEY,
    data_hora          TIMESTAMP NOT NULL,
    especialidade      VARCHAR(120) NOT NULL,
    profissional_saude VARCHAR(120) NOT NULL,
    paciente_id        BIGINT NOT NULL REFERENCES paciente (id),
    unidade_id         BIGINT NOT NULL REFERENCES unidade (id),
    status_agendamento VARCHAR(40) NOT NULL
);

INSERT INTO agendamento (data_hora, especialidade, profissional_saude, paciente_id, unidade_id, status_agendamento) VALUES
    ('2026-09-02 10:15:00', 'Dermatologia', 'Dra. Helena Costa', 6, 3, 'AGUARDANDO_CONFIRMACAO_PACIENTE'),
    ('2026-08-28 08:00:00', 'Exame — Coleta de sangue', 'Laboratório central', 2, 1, 'PACIENTE_CONFIRMOU'),
    ('2026-08-26 14:30:00', 'Cardiologia', 'Dr. Rafael Lima', 1, 1, 'AGUARDANDO_CONFIRMACAO_PACIENTE'),
    ('2026-08-18 09:30:00', 'Clínico Geral', 'Dr. Paulo Nunes', 3, 1, 'PRESENCA_PACIENTE'),
    ('2026-08-11 16:00:00', 'Ortopedia', 'Dra. Mariana Duarte', 4, 2, 'FALTA_PACIENTE'),
    ('2026-08-05 11:00:00', 'Oftalmologia', 'Dr. Carlos Mendes', 5, 1, 'CANCELADO_PELA_UNIDADE');
