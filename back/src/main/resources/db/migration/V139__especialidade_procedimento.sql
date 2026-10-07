-- Procedimento vinculado à ESPECIALIDADE, usado no lançamento automático do agendamento na importação do SIRESP.
-- Substitui a config global SIRESP_PROCEDIMENTO_PADRAO_ID (o procedimento agora é por especialidade). Opcional (NULL).
ALTER TABLE especialidade ADD COLUMN procedimento_id BIGINT;
ALTER TABLE especialidade ADD CONSTRAINT fk_especialidade_procedimento
    FOREIGN KEY (procedimento_id) REFERENCES procedimento (id);

-- Remove a config global antiga (não é mais usada — a regra virou por especialidade).
DELETE FROM configuracao WHERE chave = 'SIRESP_PROCEDIMENTO_PADRAO_ID';
