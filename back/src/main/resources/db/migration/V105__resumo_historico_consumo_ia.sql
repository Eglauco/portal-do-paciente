-- Consumo ACUMULADO de IA do "Resumo do histórico" do paciente (tela Prontuário Médico). O resumo é
-- regenerado a cada documento analisado; estas colunas SOMAM o gasto de todas as gerações para
-- mostrar ao médico o total consumido. Nulo = ainda não gerado.
ALTER TABLE paciente ADD COLUMN resumo_historico_modelo_ia     VARCHAR(60);
ALTER TABLE paciente ADD COLUMN resumo_historico_tokens_entrada BIGINT;
ALTER TABLE paciente ADD COLUMN resumo_historico_tokens_saida   BIGINT;
ALTER TABLE paciente ADD COLUMN resumo_historico_custo_usd      NUMERIC(12, 6);
ALTER TABLE paciente ADD COLUMN resumo_historico_geracoes       INTEGER;
