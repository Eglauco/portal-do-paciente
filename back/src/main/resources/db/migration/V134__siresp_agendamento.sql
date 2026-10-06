-- Criação automática de agendamento a partir do SIRESP.
-- 1) Liga cada registro ao agendamento gerado (null = ainda não gerado) — idempotência + dedup.
ALTER TABLE siresp ADD COLUMN agendamento_id BIGINT;

-- 2) Índice para a dedup por ID_AGE_CONSULTA_HOR (não criar 2 agendamentos para a mesma consulta ao reimportar).
CREATE INDEX ix_siresp_age_consulta_hor ON siresp (id_age_consulta_hor);

-- 3) Procedimento padrão usado nos agendamentos do SIRESP (o XML não traz procedimento). Começa NULL:
--    enquanto não for configurado, o diagnóstico acusa "falta informação" e nada é criado.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_numerico) VALUES
  ('SIRESP — Procedimento padrão',
   'Procedimento usado nos agendamentos criados a partir do SIRESP (o XML do CROSS não traz procedimento).',
   'SIRESP_PROCEDIMENTO_PADRAO_ID', 'NUMERICO', NULL);
