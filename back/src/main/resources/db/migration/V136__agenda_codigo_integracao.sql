-- Código da AGENDA no sistema externo (CROSS: ID_AGE_CONSULTA). É a chave que agrupa os horários na importação do
-- SIRESP: um mesmo ID_AGE_CONSULTA (vários pacientes/horários no mesmo slot) passa a reaproveitar UMA agenda, em vez
-- de criar uma agenda por registro. Opcional (agendas manuais e as importadas antes desta versão ficam NULL) e ÚNICO
-- quando preenchido — índice único PARCIAL permite múltiplos NULL, igual à especialidade (V85).
ALTER TABLE agenda ADD COLUMN codigo_integracao VARCHAR(60);

CREATE UNIQUE INDEX uk_agenda_codigo_integracao
    ON agenda (codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;
