-- Índices para as agregações dos dashboards: todo endpoint filtra por unidade +
-- faixa de data/hora. Sem eles cada carga faz varredura completa das tabelas que
-- mais crescem (agendamento, mensagem). Compostos (unidade_id, data) onde a tabela
-- tem unidade_id; só a coluna de data onde o escopo de unidade vem por join.
CREATE INDEX idx_agendamento_unidade_data ON agendamento (unidade_id, data_hora);
CREATE INDEX idx_chat_unidade_criado ON chat (unidade_id, criado_em);
CREATE INDEX idx_manifestacao_unidade_criado ON manifestacao (unidade_id, criado_em);
CREATE INDEX idx_mensagem_enviada ON mensagem (enviada_em);
CREATE INDEX idx_manifestacao_mensagem_criado ON manifestacao_mensagem (criado_em);
CREATE INDEX idx_nps_criado ON nps (criado_em);
CREATE INDEX idx_nps_respondido ON nps (respondido_em);
