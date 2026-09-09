-- Configuração de exemplo (BOOLEANO): no feed da rede social, mostrar só as iniciais
-- do autor ("M. D.") em vez do nome. Ligada por padrão (mais privado); o admin pode
-- desligar para exibir o nome completo. Vale para comentários NOVOS (o nome é capturado
-- no momento do comentário).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano)
VALUES ('Nome abreviado no feed',
        'Mostrar apenas as iniciais do paciente (ex.: "M. D.") em vez do nome nos comentários do feed.',
        'NOME_ABREVIADO_NO_FEED', 'BOOLEANO', TRUE);
