-- Login por Telefone + CPF + Data: o paciente deixa de ter "telefone principal".
-- O campo escalar paciente.telefone sai; a tabela paciente_telefone passa a ser a ÚNICA
-- fonte de telefones (e o número digitado no login é conferido contra ela).
--
--   1) preserva os números: copia o telefone principal (quando houver) para a lista,
--      sem duplicar o que já estiver lá;
--   2) remove a coluna (o índice único uk_paciente_telefone já caiu na V91);
--   3) garante unicidade por paciente na lista (a dedup só existia em memória).

INSERT INTO paciente_telefone (paciente_id, numero)
SELECT p.id, p.telefone
FROM paciente p
WHERE p.telefone IS NOT NULL AND p.telefone <> ''
  AND NOT EXISTS (
      SELECT 1 FROM paciente_telefone pt
      WHERE pt.paciente_id = p.id AND pt.numero = p.telefone
  );

ALTER TABLE paciente DROP COLUMN telefone;

-- Remove duplicatas remanescentes (histórico) antes de criar o índice único.
DELETE FROM paciente_telefone a
USING paciente_telefone b
WHERE a.ctid < b.ctid
  AND a.paciente_id = b.paciente_id
  AND a.numero = b.numero;

CREATE UNIQUE INDEX uk_paciente_telefone_numero ON paciente_telefone (paciente_id, numero);
