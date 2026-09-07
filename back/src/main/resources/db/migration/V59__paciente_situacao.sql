-- Soft-delete do paciente: situação do CADASTRO (ATIVO/INATIVO). Paciente nunca é
-- excluído — é inativado (some dos seletores e fica somente-leitura) e pode ser
-- reativado. Não confundir com paciente.ativo, que é a liberação de ACESSO AO APP.
-- Todos os cadastros existentes começam ATIVO.

ALTER TABLE paciente ADD COLUMN situacao VARCHAR(20) NOT NULL DEFAULT 'ATIVO';
