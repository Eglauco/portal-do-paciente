-- Auditoria granular: `rotulo` guarda o rótulo já "congelado" de cada alteração
-- (ex.: "Responsável João Jr — Rede social"), preservado mesmo que o responsável seja
-- renomeado/removido depois. Com isso, uma troca de UMA permissão de UM responsável vira
-- uma única linha, em vez do bloco inteiro de responsáveis.
--
-- `campo` passa a guardar um código estável (string livre: NOME, RESPONSAVEL,
-- TELEFONE_ADICIONAL, …) em vez de um enum fixo — sem mudança de tipo da coluna.

ALTER TABLE paciente_log_alteracao ADD COLUMN rotulo VARCHAR(255);
