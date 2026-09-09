-- Recibo de leitura do paciente no chat: até quando o paciente já leu a conversa
-- (high-water-mark). Mensagem da unidade mais nova que isto = ainda não lida pelo
-- paciente (indicador no app do paciente + recibo "lido" para o atendente).
ALTER TABLE chat ADD COLUMN paciente_leu_em TIMESTAMP;
