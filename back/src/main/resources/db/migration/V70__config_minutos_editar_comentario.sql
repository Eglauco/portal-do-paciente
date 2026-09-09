-- Configuração de exemplo (prova do modelo): janela de edição do comentário na rede social.
-- Antes era um número fixo (15 min) no código; agora a regra lê este valor e o admin ajusta.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_numerico)
VALUES ('Janela de edição de comentário',
        'Minutos que o paciente tem para editar o próprio comentário na rede social.',
        'MINUTOS_PARA_EDITAR_COMENTARIO', 'NUMERICO', 15);
