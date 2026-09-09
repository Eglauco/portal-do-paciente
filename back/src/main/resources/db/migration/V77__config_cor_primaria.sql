-- Novo tipo de configuração COR (hex #RRGGBB) guardado em valor_cor. Semente do tema da
-- plataforma: o front (e depois o app) leem a cor primária em GET /tema, que deriva os
-- demais tons. Valor inicial = o verde atual (#0E8C7F), então nada muda visualmente.
ALTER TABLE configuracao ADD COLUMN valor_cor VARCHAR(9);

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_cor)
VALUES ('Cor primária da plataforma',
        'Cor predominante (RGB) da plataforma. O sistema deriva os demais tons a partir dela no front e no app.',
        'COR_PRIMARIA_PLATAFORMA', 'COR', '#0E8C7F');
