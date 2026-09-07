-- Soft-delete/acesso do responsável do paciente. Responsável com QUALQUER lançamento no
-- sistema (chat, SAU, feed, agendamento_log, nps) não pode mais ser removido — a remoção
-- zerava o responsavel_id (ON DELETE SET NULL) e falsificava a autoria (virava do paciente).
-- Em vez de remover, inativa-se: perde acesso ao perfil no app, mas a autoria histórica
-- é preservada. Todos os responsáveis existentes começam ATIVO.

ALTER TABLE responsavel ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
