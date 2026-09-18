-- Login por CPF — Fase 2 (o CORTE). O telefone deixa de ser identidade: passa a ser só o
-- canal do OTP. A conta do app é agora por CPF + aparelho.
--
-- Sessões atuais são ZERADAS (sistema em testes → todos relogam), então:
--   1) esvazia conta_app e remove a chave/coluna por telefone; o CPF vira NOT NULL (tabela vazia);
--   2) o telefone principal do paciente deixa de ser único (pai e filho podem compartilhar);
--   3) limpa a sessão espelhada no paciente (dispositivo_ativo) para forçar o relogin.
--
-- NÃO torna paciente.cpf / responsavel.cpf NOT NULL aqui: os cadastros de teste antigos
-- precisam ser preenchidos manualmente antes; o NOT NULL entra numa migration posterior.

DELETE FROM conta_app;

DROP INDEX IF EXISTS uk_conta_app_telefone;
ALTER TABLE conta_app DROP COLUMN telefone;
ALTER TABLE conta_app ALTER COLUMN cpf SET NOT NULL;

DROP INDEX IF EXISTS uk_paciente_telefone;

UPDATE paciente SET dispositivo_ativo = NULL WHERE dispositivo_ativo IS NOT NULL;
