-- Relocacao do schema PADRAO (Design B): o inquilino PADRAO deixa de apontar p/ 'public' (que vira
-- plataforma pura, so com as tabelas compartilhadas) e passa a apontar p/ 'principal' (schema NOMEADO com
-- as tabelas de dominio). Com isso idPadrao()/fallback de login e o schema que o Hibernate valida no boot
-- passam a usar 'principal'. O schema 'principal' em si e criado/migrado no boot (db/migration) por
-- ProvisionamentoService.garantirInquilinoPadrao. Idempotente (so atua enquanto a linha ainda for 'public').
UPDATE public.inquilino SET nome = 'Principal', schema_name = 'principal' WHERE schema_name = 'public';
