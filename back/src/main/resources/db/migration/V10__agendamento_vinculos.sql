-- Converte especialidade e profissional de texto para vínculo (FK) e adiciona procedimento.

ALTER TABLE agendamento ADD COLUMN especialidade_id      BIGINT;
ALTER TABLE agendamento ADD COLUMN profissional_saude_id BIGINT;
ALTER TABLE agendamento ADD COLUMN procedimento_id       BIGINT;

-- Migra os dados existentes casando pelo nome; quem não casar recebe um valor padrão.
UPDATE agendamento a SET especialidade_id = e.id
    FROM especialidade e WHERE e.nome = a.especialidade;
UPDATE agendamento SET especialidade_id = (SELECT id FROM especialidade ORDER BY id LIMIT 1)
    WHERE especialidade_id IS NULL;

UPDATE agendamento a SET profissional_saude_id = p.id
    FROM profissional p WHERE p.nome = a.profissional_saude;
UPDATE agendamento SET profissional_saude_id = (SELECT id FROM profissional ORDER BY id LIMIT 1)
    WHERE profissional_saude_id IS NULL;

UPDATE agendamento SET procedimento_id = (SELECT id FROM procedimento WHERE nome = 'Consulta médica' LIMIT 1);

-- Remove as colunas de texto antigas.
ALTER TABLE agendamento DROP COLUMN especialidade;
ALTER TABLE agendamento DROP COLUMN profissional_saude;

-- Torna obrigatório e cria as chaves estrangeiras.
ALTER TABLE agendamento ALTER COLUMN especialidade_id      SET NOT NULL;
ALTER TABLE agendamento ALTER COLUMN profissional_saude_id SET NOT NULL;
ALTER TABLE agendamento ALTER COLUMN procedimento_id       SET NOT NULL;

ALTER TABLE agendamento ADD CONSTRAINT fk_agend_especialidade FOREIGN KEY (especialidade_id) REFERENCES especialidade (id);
ALTER TABLE agendamento ADD CONSTRAINT fk_agend_profissional  FOREIGN KEY (profissional_saude_id) REFERENCES profissional (id);
ALTER TABLE agendamento ADD CONSTRAINT fk_agend_procedimento  FOREIGN KEY (procedimento_id) REFERENCES procedimento (id);
