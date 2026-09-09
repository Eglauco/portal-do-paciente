-- O nome exibido do autor do comentário passa a ser resolvido em tempo de LEITURA
-- pelos ids: paciente_id -> nome do paciente; usuario_id -> "Administração"; nenhum ->
-- "Paciente". A abreviação (NOME_ABREVIADO_NO_FEED) também passa a valer na leitura, então
-- o nome nunca mais fica "congelado" no comentário. A coluna gravada 'autor' deixa de ser
-- usada e é removida para não gerar confusão.
ALTER TABLE comentario DROP COLUMN autor;
