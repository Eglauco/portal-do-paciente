-- Convite por SMS: o TEXTO da mensagem vira parâmetro (antes era fixo no código). Agora o texto é a
-- mensagem INTEIRA (o admin inclui os links das lojas no próprio texto), editado inline junto do
-- liga/desliga SMS_CONVITE_APP_HABILITADO (obrigatório quando habilitado). Com isso os 2 configs de
-- link de loja deixam de existir. Aditiva/idempotente; aplicada em todos os inquilinos no boot.

-- Novo parâmetro de texto, semeado com a mensagem que estava fixa no código (intro + link Android).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto)
SELECT 'Convite por SMS — Texto da mensagem',
       'Texto completo do SMS de convite para baixar o app, enviado quando o convite está habilitado. É a mensagem inteira — inclua os links das lojas no próprio texto. Editado junto do liga/desliga do convite.',
       'SMS_CONVITE_APP_TEXTO', 'TEXTO',
       'Olá! Você tem um atendimento agendado. Baixe nosso aplicativo para acompanhar e confirmar. Android: https://play.google.com/store/apps/details?id=br.com.eglauco.integrasaude'
WHERE NOT EXISTS (SELECT 1 FROM configuracao WHERE chave = 'SMS_CONVITE_APP_TEXTO');

-- Os links de loja agora fazem parte do texto; removê-los da configuração por-inquilino.
DELETE FROM configuracao WHERE chave IN ('SMS_CONVITE_APP_LINK_ANDROID', 'SMS_CONVITE_APP_LINK_IOS');
