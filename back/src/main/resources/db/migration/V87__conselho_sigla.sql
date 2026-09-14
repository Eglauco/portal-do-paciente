-- Sigla do conselho separada do nome (ex.: sigla "CRM", nome "Conselho Regional de Medicina").

ALTER TABLE conselho ADD COLUMN sigla VARCHAR(20);

-- Separa os registros semeados no formato "SIGLA — Nome completo" (V86) em sigla + nome.
UPDATE conselho
SET sigla = split_part(nome, ' — ', 1),
    nome  = split_part(nome, ' — ', 2)
WHERE nome LIKE '% — %';

-- Rede de segurança: qualquer registro fora do padrão fica com uma sigla derivada
-- (evita violar o NOT NULL a seguir); o admin ajusta pela tela depois.
UPDATE conselho SET sigla = left(nome, 20) WHERE sigla IS NULL OR sigla = '';

ALTER TABLE conselho ALTER COLUMN sigla SET NOT NULL;
