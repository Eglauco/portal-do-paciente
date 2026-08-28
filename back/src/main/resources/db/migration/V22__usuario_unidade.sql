-- Unidade de saúde ativa ("logada") do usuário: usada para filtrar e fixar a
-- unidade nas telas do admin. É atualizada quando o usuário troca de unidade.
ALTER TABLE usuario ADD COLUMN unidade_id BIGINT REFERENCES unidade (id);

-- O admin começa na primeira unidade cadastrada (ex.: "Unidade de Saúde 01").
UPDATE usuario
SET unidade_id = (SELECT id FROM unidade ORDER BY id LIMIT 1)
WHERE lower(email) = 'adm@unidadesaude.com.br';
