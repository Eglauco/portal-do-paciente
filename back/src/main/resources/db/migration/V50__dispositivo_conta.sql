-- O aparelho de push passa a pertencer à CONTA (telefone), não ao perfil ativo.
-- Assim um evento de qualquer perfil que a conta acessa (proprio + dependentes)
-- alcança o aparelho, mesmo logado em outro perfil. paciente_id fica como legado.

ALTER TABLE dispositivo ADD COLUMN conta_id BIGINT;

ALTER TABLE dispositivo
    ADD CONSTRAINT fk_dispositivo_conta
    FOREIGN KEY (conta_id) REFERENCES conta_app (id) ON DELETE SET NULL;

CREATE INDEX idx_dispositivo_conta ON dispositivo (conta_id);
