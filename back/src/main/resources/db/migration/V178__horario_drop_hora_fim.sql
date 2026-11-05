-- 'horaFim' do Horario removido: não era usado em nenhuma regra de negócio. Tirado de todo o código (back/front/app
-- + importação de agenda) e também do banco, por decisão do dono (04/11/2026). IF EXISTS torna a migração segura/
-- idempotente; aplica em todos os inquilinos no boot (flywayMigrationStrategy, ver [[migration-multi-tenant-no-boot]]).
ALTER TABLE horario DROP COLUMN IF EXISTS hora_fim;
