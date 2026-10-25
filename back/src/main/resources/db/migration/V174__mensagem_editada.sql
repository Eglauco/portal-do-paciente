-- Edição de mensagem (estilo WhatsApp): quando a mensagem foi editada (texto alterado pelo autor
-- dentro da janela de 15 min). Nulo quando nunca foi editada; o cliente mostra o rótulo "Editada".
-- Aditivo/idempotente; aplicado em todos os inquilinos no boot (flywayMigrationStrategy).

ALTER TABLE mensagem ADD COLUMN editada_em TIMESTAMP;
