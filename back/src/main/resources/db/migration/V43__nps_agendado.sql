-- Agendamento de disparo do NPS: cada procedimento define quantas horas APÓS a
-- presença do paciente o NPS deve ser disparado (0 = na hora). O NPS é criado ao
-- registrar a presença, mas só fica visível/enviado ao paciente quando disparado
-- (disparado_em preenchido). Um job dispara os que chegaram na hora.
ALTER TABLE procedimento ADD COLUMN horas_nps INT NOT NULL DEFAULT 0;

ALTER TABLE nps ADD COLUMN disparar_em TIMESTAMP;
ALTER TABLE nps ADD COLUMN disparado_em TIMESTAMP;

-- NPS já existentes foram disparados na criação: mantêm-se visíveis e fora do
-- agendador (disparado_em preenchido) para não reenviar.
UPDATE nps SET disparar_em = criado_em, disparado_em = criado_em;
