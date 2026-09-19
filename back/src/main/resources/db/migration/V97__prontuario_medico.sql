-- Tela "Prontuário Médico" (médico busca o paciente e vê o histórico em linha do tempo) + resumo
-- geral do histórico do paciente gerado por IA (sob demanda), armazenado para não refazer a cada visita.

-- Resumo clínico do histórico completo do paciente (síntese por IA dos resumos por documento).
ALTER TABLE paciente ADD COLUMN resumo_historico_ia TEXT;
ALTER TABLE paciente ADD COLUMN resumo_historico_gerado_em TIMESTAMP;

-- Concede a nova tela PRONTUARIO_MEDICO a todo perfil que já libera PRONTUARIOS (uso imediato;
-- o enum Tela é VARCHAR livre — sem migration de schema). Admin pode ajustar depois na tela de Perfis.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT DISTINCT pt.perfil_id, 'PRONTUARIO_MEDICO'
FROM perfil_tela pt
WHERE pt.tela = 'PRONTUARIOS'
ON CONFLICT (perfil_id, tela) DO NOTHING;
