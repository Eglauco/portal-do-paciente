-- Opção no cadastro do Termo: além do paciente, o profissional de saúde do atendimento também assina.
ALTER TABLE termo_procedimento
    ADD COLUMN profissional_assina BOOLEAN NOT NULL DEFAULT FALSE;
