-- Fase 0 (censo do paciente_login): backfill dos pacientes e responsaveis LEGADOS que ja tem CPF mas
-- ainda nao tem o ponteiro de roteamento (cpf, inquilino_id) no public.paciente_login. Necessario para o
-- app COMPARTILHADO resolver a clinica do CPF no login (quando o inquilino nao vem assado no build).
--
-- Roda POR-TENANT (db/migration, em cada schema de inquilino): resolve o inquilino_id DESTE schema via
-- public.inquilino.schema_name = current_schema(). Idempotente (ON CONFLICT DO NOTHING). So CPFs validos
-- (11 digitos). Num tenant recem-provisionado as tabelas estao vazias -> nao insere nada.
INSERT INTO public.paciente_login (cpf, inquilino_id, criado_em)
SELECT cpfs.cpf, inq.id, now()
FROM public.inquilino inq
CROSS JOIN (
    SELECT cpf FROM paciente WHERE cpf ~ '^[0-9]{11}$'
    UNION
    SELECT cpf FROM responsavel WHERE cpf ~ '^[0-9]{11}$'
) cpfs
WHERE inq.schema_name = current_schema()
ON CONFLICT (cpf, inquilino_id) DO NOTHING;
