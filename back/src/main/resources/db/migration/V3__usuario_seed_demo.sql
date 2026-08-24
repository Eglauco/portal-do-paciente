-- Dados de exemplo para demonstrar a paginação (total passa a 120 usuários).
INSERT INTO usuario (nome, email)
SELECT 'Colaborador ' || g,
       'colaborador' || g || '@unidadesaude.com.br'
FROM generate_series(1, 112) AS g;
