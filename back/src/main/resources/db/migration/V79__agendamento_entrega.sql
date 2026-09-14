-- Rastreio de ENTREGA da notificação de agendamento, por destinatário (Fase 1).
-- Uma linha por pessoa autorizada a receber (o paciente + cada responsável ativo com
-- acesso a Agendamentos), com o estado no momento do disparo:
--   PACIENTE_SEM_APLICATIVO  -> a pessoa não tem nenhum aparelho com push registrado
--   NOTIFICACAO_ENVIADA      -> a Expo aceitou o envio para ao menos um aparelho da pessoa
--   SEM_NOTIFICACAO_ATIVA    -> tinha aparelho(s), mas o envio não foi aceito (token morto / falha)
-- nome/telefone são congelados (rótulo estável para o admin, como na auditoria do cadastro).
CREATE TABLE agendamento_entrega (
    id             BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL,
    tipo           VARCHAR(20) NOT NULL,   -- PACIENTE | RESPONSAVEL
    responsavel_id BIGINT,                 -- preenchido quando tipo = RESPONSAVEL
    nome           VARCHAR(160) NOT NULL,
    telefone       VARCHAR(20),
    estado         VARCHAR(30) NOT NULL,
    criado_em      TIMESTAMP NOT NULL,
    CONSTRAINT fk_ag_entrega_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamento (id) ON DELETE CASCADE,
    CONSTRAINT fk_ag_entrega_responsavel FOREIGN KEY (responsavel_id) REFERENCES responsavel (id) ON DELETE SET NULL
);

CREATE INDEX idx_ag_entrega_agendamento ON agendamento_entrega (agendamento_id);

-- Resumo denormalizado no agendamento (para a lista do admin, sem N+1). NULL = sem dado
-- (agendamentos anteriores a esta funcionalidade).
ALTER TABLE agendamento ADD COLUMN entrega_resumo VARCHAR(30);
