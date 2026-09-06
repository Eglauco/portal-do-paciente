-- Chat: registra o responsável que enviou a mensagem representando o paciente
-- (perfil dependente no app). Nulo quando foi o próprio paciente.
-- ON DELETE SET NULL (o marcador some se o vínculo do responsável for removido).
--
-- Atenção: NÃO confundir com chat.responsavel_id, que é o ATENDENTE (usuário do
-- back-office) que assumiu a conversa. Aqui, na mensagem, é o responsável do PACIENTE.

ALTER TABLE mensagem ADD COLUMN responsavel_id BIGINT;
ALTER TABLE mensagem
    ADD CONSTRAINT fk_mensagem_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL;
CREATE INDEX idx_mensagem_responsavel ON mensagem (responsavel_id);
