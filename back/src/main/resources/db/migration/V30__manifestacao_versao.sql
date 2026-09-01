-- Trava otimista (@Version) na manifestação: sustenta a regra de "1 mensagem por
-- vez" sob concorrência (dois envios simultâneos do mesmo lado → só um grava).
ALTER TABLE manifestacao ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
