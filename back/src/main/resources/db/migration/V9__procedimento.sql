-- Tabela de procedimentos.

CREATE TABLE IF NOT EXISTS procedimento (
    id      BIGSERIAL PRIMARY KEY,
    nome    VARCHAR(120) NOT NULL,
    preparo TEXT
);

INSERT INTO procedimento (nome, preparo) VALUES
    ('Consulta médica', NULL),
    ('Exame de laboratório', 'Jejum de 8 a 12 horas. Levar o pedido médico e documento com foto.'),
    ('Exame de raio-x', 'Retirar objetos metálicos. Informar se há suspeita de gravidez.'),
    ('Ultrassonografia', 'Beber 1 litro de água 1 hora antes do exame e não urinar até a realização.'),
    ('Eletrocardiograma', 'Evitar o uso de cremes na região do tórax. Não é necessário jejum.'),
    ('Retorno', NULL);
