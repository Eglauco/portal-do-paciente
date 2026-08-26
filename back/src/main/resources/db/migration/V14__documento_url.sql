-- Adiciona a URL do arquivo (no S3) ao documento do prontuário.
ALTER TABLE documento ADD COLUMN url TEXT;
