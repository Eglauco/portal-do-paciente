-- Fase B: a CREDENCIAL do paciente (PIN + lockout) saiu do conta_app POR-INQUILINO e virou CENTRAL
-- (public.paciente_identidade, V157). O conta_app agora e so a SESSAO por-clinica: cpf +
-- dispositivo_ativo. Migration por-tenant (db/migration, roda em cada schema de inquilino). Idempotente.

-- BACKFILL: preserva o PIN/lockout existente ANTES de dropar. Migra conta_app (deste schema) ->
-- public.paciente_identidade por CPF. Pre-Fase-B o CPF era UNICO GLOBAL (so V158 abre o 1->N), entao
-- cada CPF estava em exatamente UM tenant no momento da migracao -> sem conflito; ON CONFLICT DO NOTHING
-- por seguranca (idempotencia/re-run). Roda na mesma conexao, entao conta_app = schema corrente e
-- public.paciente_identidade = tabela qualificada.
INSERT INTO public.paciente_identidade (cpf, senha_hash, senha_tentativas, criado_em, atualizado_em)
SELECT cpf, senha_hash, COALESCE(senha_tentativas, 0), now(), now()
FROM conta_app
WHERE senha_hash IS NOT NULL
ON CONFLICT (cpf) DO NOTHING;

ALTER TABLE conta_app DROP COLUMN IF EXISTS senha_hash;
ALTER TABLE conta_app DROP COLUMN IF EXISTS senha_tentativas;
