-- Suporte ao tipo EXAME na mesma tabela siresp (além de CONSULTA). Tipo do registro + colunas específicas do exame.
-- Registros existentes ficam como CONSULTA (default); as colunas de exame ficam nulas neles (e vice-versa).
ALTER TABLE siresp ADD COLUMN tipo_registro VARCHAR(20) NOT NULL DEFAULT 'CONSULTA';

-- Campos do layout de EXAME (texto cru, como os demais). Nulos quando o registro é CONSULTA.
ALTER TABLE siresp ADD COLUMN tipo_exame        TEXT;
ALTER TABLE siresp ADD COLUMN id_age_exame_hor  TEXT;
ALTER TABLE siresp ADD COLUMN id_age_exame      TEXT;
ALTER TABLE siresp ADD COLUMN age_exame_nome    TEXT;
ALTER TABLE siresp ADD COLUMN id_associacao     TEXT;
ALTER TABLE siresp ADD COLUMN nome_associacao   TEXT;
ALTER TABLE siresp ADD COLUMN id_exame          TEXT;
ALTER TABLE siresp ADD COLUMN cod_exame         TEXT;
ALTER TABLE siresp ADD COLUMN nome_exame        TEXT;
ALTER TABLE siresp ADD COLUMN tipo_tabela       TEXT;

-- Índice para a dedup do exame por ID_AGE_EXAME_HOR (análogo ao ix_siresp_age_consulta_hor da V134).
CREATE INDEX ix_siresp_age_exame_hor ON siresp (id_age_exame_hor);
