-- Imagem de fundo (white-label) do painel azul do login/trocar-unidade. Reusa o tipo IMAGEM
-- (coluna valor_imagem já existe desde V82). Valor NULL => sem imagem (só o azul), até o admin subir uma.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_imagem)
VALUES ('Imagem de fundo do login',
        'Imagem exibida atrás do painel azul do login e da tela de trocar unidade. O azul cobre por cima em opacidade alta, então a imagem fica sutil. Sem imagem, mostra só o azul.',
        'LOGIN_FUNDO', 'IMAGEM', NULL);
