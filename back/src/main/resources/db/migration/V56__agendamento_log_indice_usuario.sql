-- Índice em usuario_id do log do agendamento: acelera o ON DELETE SET NULL quando
-- um atendente (usuario) é removido. Sem ele, apagar um usuário faz seq scan na
-- tabela de auditoria (que cresce 1 linha por troca de status). Espelha o índice
-- de responsavel_id (o outro ator que sofre SET NULL por remoção rotineira).
-- paciente_id não é indexado de propósito: agendamento.paciente_id é RESTRICT, então
-- um paciente com agendamento (logo com logs) nunca é apagável — o SET NULL não dispara.

CREATE INDEX idx_agendamento_log_usuario ON agendamento_log (usuario_id);
