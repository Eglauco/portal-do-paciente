-- Ajuste do texto de descrição da config LOGIN_FUNDO (V83 já aplicada é imutável → UPDATE aqui).
UPDATE configuracao
SET descricao = 'Imagem exibida atrás do painel de login e da tela de trocar unidade. A cor da plataforma cobre por cima em opacidade alta, então a imagem fica sutil. Sem imagem, mostra somente a cor da plataforma.'
WHERE chave = 'LOGIN_FUNDO';
