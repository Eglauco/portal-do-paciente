-- Rastreabilidade da integração SIRESP/CROSS nas telas de Agenda/Horário:
--   • agenda.nome            → nome da agenda no CROSS (AGE_CONSULTA_NOME), replicado na importação. Editável (rótulo).
--   • horario.codigo_integracao → código do horário no CROSS (ID_AGE_CONSULTA_HOR). Chave de rastreio, só-leitura.
-- (agenda.codigo_integracao = ID_AGE_CONSULTA já veio na V136.) Códigos opcionais (agendas/horários manuais ficam NULL)
-- e ÚNICOS quando preenchidos — índice único PARCIAL permite múltiplos NULL, igual à especialidade (V85) e à agenda (V136).

ALTER TABLE agenda  ADD COLUMN nome              VARCHAR(255);
ALTER TABLE horario ADD COLUMN codigo_integracao VARCHAR(60);

CREATE UNIQUE INDEX uk_horario_codigo_integracao
    ON horario (codigo_integracao)
    WHERE codigo_integracao IS NOT NULL;
