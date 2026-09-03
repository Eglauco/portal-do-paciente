-- Lembretes de um procedimento: cada procedimento pode ter vários lembretes, cada
-- um com um texto (mensagem ao paciente) e a antecedência (em horas) do agendamento
-- em que deve ser disparado. Um job periódico dispara push + notificação.
CREATE TABLE lembrete (
    id                 BIGSERIAL PRIMARY KEY,
    procedimento_id    BIGINT NOT NULL REFERENCES procedimento (id) ON DELETE CASCADE,
    texto              VARCHAR(300) NOT NULL,
    horas_antecedencia INT NOT NULL,
    criado_em          TIMESTAMP NOT NULL
);
CREATE INDEX idx_lembrete_procedimento ON lembrete (procedimento_id);

-- Registro de disparo: garante que cada lembrete dispare no máximo UMA vez por
-- agendamento (a UNIQUE trava a corrida entre execuções do job).
CREATE TABLE lembrete_disparo (
    id              BIGSERIAL PRIMARY KEY,
    lembrete_id     BIGINT NOT NULL REFERENCES lembrete (id) ON DELETE CASCADE,
    agendamento_id  BIGINT NOT NULL REFERENCES agendamento (id) ON DELETE CASCADE,
    criado_em       TIMESTAMP NOT NULL,
    UNIQUE (lembrete_id, agendamento_id)
);
CREATE INDEX idx_lembrete_disparo_agendamento ON lembrete_disparo (agendamento_id);
