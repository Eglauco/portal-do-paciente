-- Antecedência mínima (em horas) para o paciente poder cancelar o agendamento.
-- Obrigatório: os procedimentos já existentes recebem 24h por padrão (o admin ajusta).
ALTER TABLE procedimento ADD COLUMN horas_cancelamento INTEGER NOT NULL DEFAULT 24;
