-- NPS: registra o responsável (cadastro) que respondeu a avaliação representando
-- o paciente (perfil dependente no app). Nulo quando foi o próprio paciente ou o
-- admin pelo back-office.
-- ON DELETE SET NULL: remover o vínculo do responsável não apaga a avaliação —
-- só o marcador "respondido por (responsável)" some (o nome deixa de ser resolvível).

ALTER TABLE nps ADD COLUMN responsavel_id BIGINT;
ALTER TABLE nps
    ADD CONSTRAINT fk_nps_responsavel
    FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL;
CREATE INDEX idx_nps_responsavel ON nps (responsavel_id);
