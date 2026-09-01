-- NPS passa a usar escala de 1 a 5 estrelas (antes 0..10). Converte os dados já
-- respondidos: estrelas = arredonda(nota / 2), limitado ao intervalo [1, 5].
-- (0->1, 1..2->1, 3..4->2, 5..6->3, 7..8->4, 9..10->5)

-- Notas por categoria.
UPDATE nps_categoria_nota
   SET nota = LEAST(5, GREATEST(1, ROUND(nota / 2.0)))::int;

-- Nota única legada (quando existir).
UPDATE nps
   SET nota = LEAST(5, GREATEST(1, ROUND(nota / 2.0)))::int
 WHERE nota IS NOT NULL;

-- Recalcula a média a partir das notas já convertidas (só nps com notas por categoria).
UPDATE nps
   SET media = sub.media
  FROM (
        SELECT nps_id, AVG(nota) AS media
          FROM nps_categoria_nota
         GROUP BY nps_id
       ) sub
 WHERE nps.id = sub.nps_id;
