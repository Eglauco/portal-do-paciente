-- Tipo de atendimento (CONSULTA/EXAME) na Agenda/Horário para DIFERENCIAR o código de integração do CROSS: os
-- id-spaces de consulta (ID_AGE_CONSULTA*) e de exame (ID_AGE_EXAME*) podem colidir numericamente, então a
-- identidade do registro importado passa a ser (tipo_atendimento + codigo_integracao). Nulo nos manuais (sem código).
ALTER TABLE agenda  ADD COLUMN tipo_atendimento VARCHAR(20);
ALTER TABLE horario ADD COLUMN tipo_atendimento VARCHAR(20);

-- Registros já com código vieram de CONSULTAS do SIRESP (exames são novos) → marca CONSULTA.
UPDATE agenda  SET tipo_atendimento = 'CONSULTA' WHERE codigo_integracao IS NOT NULL;
UPDATE horario SET tipo_atendimento = 'CONSULTA' WHERE codigo_integracao IS NOT NULL;

-- Troca os índices únicos de (código) para (tipo + código): o mesmo número pode existir numa consulta e num exame,
-- mas continua único dentro do mesmo tipo. Índice PARCIAL (só quando há código) — manuais (código nulo) ficam de fora.
DROP INDEX uk_agenda_codigo_integracao;
CREATE UNIQUE INDEX uk_agenda_codigo_integracao
    ON agenda (tipo_atendimento, codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;

DROP INDEX uk_horario_codigo_integracao;
CREATE UNIQUE INDEX uk_horario_codigo_integracao
    ON horario (tipo_atendimento, codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;
