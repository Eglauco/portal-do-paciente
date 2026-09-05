-- Foto do paciente (URL do objeto no S3, pasta "foto-paciente").
-- Alterável pelo próprio paciente no app; back-office não usa.
ALTER TABLE paciente ADD COLUMN foto_url VARCHAR(512);
