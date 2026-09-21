-- Nº de gerações da IA por documento (análise inicial + reanálises). Os campos tokens/custo do
-- documento passam a ACUMULAR o consumo de todas as gerações (antes eram só a última). Nulo = ainda
-- não analisado. Registros anteriores ficam com o snapshot da última análise até uma nova reanálise.
ALTER TABLE documento ADD COLUMN geracoes_ia INTEGER;
