-- Renomeia o status "VALIDADO" para "ALTERACAO_CONFIRMADA" (a validação humana passou a ter duas
-- saídas: confirmar a alteração OU marcar sem alteração). As colunas são VARCHAR sem CHECK, então
-- basta atualizar os dados existentes. Também adiciona a observação opcional da decisão humana.

UPDATE documento  SET status_analise = 'ALTERACAO_CONFIRMADA' WHERE status_analise = 'VALIDADO';
UPDATE prontuario SET status_alerta  = 'ALTERACAO_CONFIRMADA' WHERE status_alerta  = 'VALIDADO';

ALTER TABLE documento ADD COLUMN observacao_validacao TEXT;
