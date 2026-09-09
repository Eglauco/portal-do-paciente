-- Renomeia a config de abreviação de nome para um nome mais claro (agora explicita que
-- vale para PACIENTE e RESPONSÁVEL, e "rede social" em vez de "feed"). Só troca
-- chave/nome/descrição; NUNCA mexe no valor_booleano, para preservar o ajuste do admin
-- (se ele já tinha desligado, continua desligado).
UPDATE configuracao
   SET chave = 'NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL',
       nome = 'Abreviar nome do paciente e responsável na rede social',
       descricao = 'Mostrar apenas as iniciais do paciente e do responsável (ex.: "M. D.") em vez do nome nos comentários do feed.'
 WHERE chave = 'NOME_ABREVIADO_NO_FEED';
