-- Chat: um registro por paciente + unidade, com histórico de mensagens.

CREATE TABLE IF NOT EXISTS chat (
    id            BIGSERIAL PRIMARY KEY,
    paciente_id   BIGINT NOT NULL REFERENCES paciente (id),
    unidade_id    BIGINT NOT NULL REFERENCES unidade (id),
    status        VARCHAR(30) NOT NULL,
    criado_em     TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_chat_paciente_unidade UNIQUE (paciente_id, unidade_id)
);

CREATE TABLE IF NOT EXISTS mensagem (
    id         BIGSERIAL PRIMARY KEY,
    chat_id    BIGINT NOT NULL REFERENCES chat (id),
    remetente  VARCHAR(20) NOT NULL,
    texto      TEXT NOT NULL,
    enviada_em TIMESTAMP NOT NULL,
    lida       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_mensagem_chat ON mensagem (chat_id);

-- ------------------------------------------------------------------
-- Dados de exemplo (chats variados por status)
-- ------------------------------------------------------------------
INSERT INTO chat (id, paciente_id, unidade_id, status, criado_em, atualizado_em) VALUES
    (1, 1, 1, 'NAO_LIDA',            '2026-08-24 09:00:00', '2026-08-24 09:15:00'),
    (2, 2, 1, 'AGUARDANDO_RESPOSTA', '2026-08-24 08:05:00', '2026-08-24 08:10:00'),
    (3, 3, 1, 'EM_ATENDIMENTO',      '2026-08-23 16:30:00', '2026-08-23 16:40:00'),
    (4, 4, 2, 'RESOLVIDO',           '2026-08-20 10:10:00', '2026-08-20 10:30:00'),
    (5, 5, 1, 'NAO_LIDA',            '2026-08-24 08:40:00', '2026-08-24 08:50:00'),
    (6, 6, 3, 'EM_ATENDIMENTO',      '2026-08-23 14:00:00', '2026-08-23 14:20:00'),
    (7, 7, 1, 'RESOLVIDO',           '2026-08-18 14:40:00', '2026-08-18 15:00:00'),
    (8, 8, 2, 'AGUARDANDO_RESPOSTA', '2026-08-22 11:00:00', '2026-08-22 11:05:00');

INSERT INTO mensagem (chat_id, remetente, texto, enviada_em, lida) VALUES
    -- Chat 1 (não lida: paciente enviou por último)
    (1, 'PACIENTE', 'Bom dia! Gostaria de remarcar minha consulta de cardiologia.', '2026-08-24 09:00:00', true),
    (1, 'UNIDADE',  'Bom dia, Mariana! Claro. Qual a melhor data para você?', '2026-08-24 09:05:00', true),
    (1, 'PACIENTE', 'Poderia ser no dia 02/09 pela manhã?', '2026-08-24 09:15:00', false),

    -- Chat 2 (aguardando resposta: última do paciente, já visualizada)
    (2, 'PACIENTE', 'Olá, meu exame de sangue já ficou pronto?', '2026-08-24 08:10:00', true),

    -- Chat 3 (em atendimento: última da unidade)
    (3, 'PACIENTE', 'Preciso de um atestado do atendimento de hoje.', '2026-08-23 16:30:00', true),
    (3, 'UNIDADE',  'Certo! Você já pode retirar na recepção ou receber pelo app. Qual prefere?', '2026-08-23 16:40:00', true),

    -- Chat 4 (resolvido)
    (4, 'PACIENTE', 'Qual o horário de funcionamento da unidade?', '2026-08-20 10:10:00', true),
    (4, 'UNIDADE',  'Funcionamos das 7h às 19h, de segunda a sexta.', '2026-08-20 10:20:00', true),
    (4, 'PACIENTE', 'Perfeito, muito obrigado!', '2026-08-20 10:30:00', true),

    -- Chat 5 (não lida: 2 mensagens do paciente sem leitura)
    (5, 'UNIDADE',  'Olá, Fernanda! Como podemos ajudar?', '2026-08-24 08:40:00', true),
    (5, 'PACIENTE', 'Estou com dores no joelho há alguns dias.', '2026-08-24 08:47:00', false),
    (5, 'PACIENTE', 'Consigo um encaixe com o ortopedista essa semana?', '2026-08-24 08:50:00', false),

    -- Chat 6 (em atendimento)
    (6, 'PACIENTE', 'Recebi a confirmação do meu exame?', '2026-08-23 14:00:00', true),
    (6, 'UNIDADE',  'Sim! Enviamos a confirmação por e-mail e ela também está no seu app.', '2026-08-23 14:20:00', true),

    -- Chat 7 (resolvido)
    (7, 'PACIENTE', 'Preciso cancelar minha consulta de amanhã.', '2026-08-18 14:40:00', true),
    (7, 'UNIDADE',  'Consulta cancelada com sucesso. Deseja remarcar?', '2026-08-18 14:55:00', true),
    (7, 'PACIENTE', 'Não, obrigada!', '2026-08-18 15:00:00', true),

    -- Chat 8 (aguardando resposta)
    (8, 'PACIENTE', 'A minha receita de uso contínuo pode ser renovada por aqui?', '2026-08-22 11:05:00', true);

-- Ajusta a sequência de ids do chat (inserimos ids manualmente).
SELECT setval(pg_get_serial_sequence('chat', 'id'), (SELECT MAX(id) FROM chat));
