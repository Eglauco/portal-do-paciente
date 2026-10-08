-- Renomeia o conceito "Procedimento" para "Configuração da Agenda" (regras do tipo de agenda:
-- preparo, prazo de cancelamento, NPS, termos/TCLE, lembretes). Só renomeia tabelas/colunas +
-- a tela de RBAC; os dados são preservados. Constraints/índices mantêm os nomes antigos (o
-- Postgres atualiza as referências ao renomear a tabela/coluna, e o ddl-validate não checa esses nomes).

-- Tabelas
ALTER TABLE procedimento        RENAME TO configuracao_agenda;
ALTER TABLE termo_procedimento  RENAME TO termo_configuracao_agenda;

-- Colunas FK procedimento_id -> configuracao_agenda_id (todas as tabelas que referenciam)
ALTER TABLE agenda                     RENAME COLUMN procedimento_id TO configuracao_agenda_id;
ALTER TABLE especialidade              RENAME COLUMN procedimento_id TO configuracao_agenda_id;
ALTER TABLE exame                      RENAME COLUMN procedimento_id TO configuracao_agenda_id;
ALTER TABLE lembrete                   RENAME COLUMN procedimento_id TO configuracao_agenda_id;
ALTER TABLE termo_configuracao_agenda  RENAME COLUMN procedimento_id TO configuracao_agenda_id;

-- Referência ao termo (TCLE) na assinatura
ALTER TABLE termo_assinatura RENAME COLUMN termo_procedimento_id TO termo_configuracao_agenda_id;

-- RBAC: a tela PROCEDIMENTOS vira CONFIGURACAO_AGENDA (perfil_tela.tela guarda o nome do enum)
UPDATE perfil_tela SET tela = 'CONFIGURACAO_AGENDA' WHERE tela = 'PROCEDIMENTOS';
