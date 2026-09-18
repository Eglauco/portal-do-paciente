-- FAQ por unidade: perguntas e respostas que alimentam a assistente virtual do chat ao vivo.
-- Cadastrado numa aba do cadastro da Unidade. A IA busca o FAQ da unidade em que o paciente abriu
-- a conversa. ON DELETE CASCADE: o FAQ é filho exclusivo da unidade (some junto se ela for excluída).
CREATE TABLE unidade_faq (
    id         BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL REFERENCES unidade (id) ON DELETE CASCADE,
    pergunta   TEXT NOT NULL,
    resposta   TEXT NOT NULL,
    ordem      INT NOT NULL DEFAULT 0
);

-- Leitura do FAQ por unidade, já na ordem de exibição.
CREATE INDEX idx_unidade_faq_unidade ON unidade_faq (unidade_id, ordem, id);
