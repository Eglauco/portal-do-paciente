-- Convite por SMS para baixar o app: disparado quando um Horário é criado (manual ou importado do SIRESP)
-- e a pessoa (paciente ou responsável) ainda NÃO tem o aplicativo. 3 parâmetros globais editáveis na tela
-- de Configurações: liga/desliga + os dois links de loja. Começa DESLIGADO (opt-in consciente do admin).
-- O envio em si usa a Twilio Messaging API (account-sid/auth-token + Messaging Service SID / From via
-- variáveis de ambiente, NÃO aqui): sem credenciais/remetente, nada é enviado (fail-open).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano) VALUES
  ('Convite por SMS — Enviar automaticamente',
   'Quando um atendimento é agendado, envia SMS convidando a baixar o app para quem (paciente ou responsável) ainda não o tem, em todos os celulares cadastrados. Requer o Twilio Messaging configurado no servidor e ao menos um link de loja preenchido.',
   'SMS_CONVITE_APP_HABILITADO', 'BOOLEANO', false);

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto) VALUES
  ('Convite por SMS — Link da loja Android',
   'Endereço do app na Google Play, incluído no SMS de convite. Em branco, o link Android é omitido da mensagem.',
   'SMS_CONVITE_APP_LINK_ANDROID', 'TEXTO',
   'https://play.google.com/store/apps/details?id=br.com.eglauco.integrasaude'),
  ('Convite por SMS — Link da loja iOS (App Store)',
   'Endereço do app na App Store, incluído no SMS de convite. Em branco, o link iOS é omitido da mensagem.',
   'SMS_CONVITE_APP_LINK_IOS', 'TEXTO', NULL);
