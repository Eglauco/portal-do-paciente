-- Código da unidade em um sistema externo (integração SIRESP/CROSS), ex.: COD_UNIDADE_EXECUTANTE.
-- Opcional; único quando preenchido (índice parcial — permite vários nulos).
ALTER TABLE unidade ADD COLUMN codigo_integracao VARCHAR(60);
CREATE UNIQUE INDEX uq_unidade_codigo_integracao ON unidade (codigo_integracao) WHERE codigo_integracao IS NOT NULL;
