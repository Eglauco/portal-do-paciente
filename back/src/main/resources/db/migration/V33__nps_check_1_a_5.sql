-- Alinha as restrições de domínio (CHECK) do NPS com a nova escala 1..5 estrelas.
-- Roda depois da V32 (que já converteu os dados), então nenhum valor viola o novo range.

ALTER TABLE nps_categoria_nota DROP CONSTRAINT ck_nps_categoria_nota;
ALTER TABLE nps_categoria_nota ADD CONSTRAINT ck_nps_categoria_nota CHECK (nota BETWEEN 1 AND 5);

ALTER TABLE nps DROP CONSTRAINT ck_nps_nota;
ALTER TABLE nps ADD CONSTRAINT ck_nps_nota CHECK (nota IS NULL OR (nota BETWEEN 1 AND 5));
