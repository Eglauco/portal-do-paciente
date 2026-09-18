-- Primeiro atendimento por IA no chat ao vivo: a assistente virtual (Claude) responde o paciente
-- enquanto NENHUM humano assumiu a conversa (chat.responsavel IS NULL). Ao escalar/erro/pedido de
-- humano, a IA "sai" (ia_encerrada) e a conversa cai na fila de assumir, como já é hoje.

-- Marca a mensagem da unidade gerada pela IA (vs. atendente humano) — para o selo na UI e auditoria.
-- Só faz sentido em mensagens com remetente = UNIDADE; nas do paciente fica sempre FALSE.
ALTER TABLE mensagem ADD COLUMN gerada_por_ia BOOLEAN NOT NULL DEFAULT FALSE;

-- A IA já encerrou a atuação nesta conversa (escalou para humano, o paciente pediu humano, ou houve
-- erro): não responde mais, mesmo com responsavel ainda nulo. Religa ao criar nova conversa (default).
ALTER TABLE chat ADD COLUMN ia_encerrada BOOLEAN NOT NULL DEFAULT FALSE;

-- Liga/desliga GLOBAL do primeiro atendimento por IA. Começa DESLIGADO: só passa a atuar quando o
-- admin ligar na tela de Configurações (e cada unidade tiver seu FAQ cadastrado). Desligado = o chat
-- vai direto para a fila humana, exatamente como funciona hoje.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano)
VALUES ('Chat ao vivo com IA (primeiro atendimento)',
        'Quando ligada, a assistente virtual faz o primeiro atendimento no chat ao vivo (responde o '
        || 'paciente com base no FAQ da unidade e nos dados dele) até um atendente assumir. Desligada, '
        || 'o chat vai direto para a fila de atendimento humano.',
        'APP_CHAT_IA_HABILITADO', 'BOOLEANO', FALSE);
