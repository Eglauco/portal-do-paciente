-- SAU: registra o responsável que atuou representando o paciente.
-- Na manifestação: quem a ABRIU. Em cada mensagem: quem a ENVIOU.
-- Nulo quando foi o próprio paciente. ON DELETE SET NULL (o marcador some se o vínculo for removido).

ALTER TABLE manifestacao ADD COLUMN responsavel_id BIGINT;
ALTER TABLE manifestacao
    ADD CONSTRAINT fk_manifestacao_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL;
CREATE INDEX idx_manifestacao_responsavel ON manifestacao (responsavel_id);

ALTER TABLE manifestacao_mensagem ADD COLUMN responsavel_id BIGINT;
ALTER TABLE manifestacao_mensagem
    ADD CONSTRAINT fk_manifestacao_mensagem_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL;
CREATE INDEX idx_manifestacao_mensagem_responsavel ON manifestacao_mensagem (responsavel_id);
