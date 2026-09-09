-- Configuração (BOOLEANO): ocultar o nome do usuário (back-office) que comenta na rede
-- social. LIGADA (padrão) → o comentário da unidade aparece como "Administração"; DESLIGADA
-- → aparece o nome completo do usuário que comentou. Ligada por padrão (mais privado).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano)
VALUES ('Ocultar nome do usuário na rede social',
        'Quando ligada, os comentários da unidade na rede social aparecem como "Administração"; desligada, mostram o nome completo do usuário que comentou.',
        'OCULTAR_NOME_USUARIO_NA_REDESOCIAL', 'BOOLEANO', TRUE);
