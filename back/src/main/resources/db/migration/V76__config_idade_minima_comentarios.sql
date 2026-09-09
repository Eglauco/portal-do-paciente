-- Configuração (NUMERICO): idade mínima (em anos) para comentar na rede social.
-- 0 = sem restrição (padrão, não muda o comportamento até o admin configurar). Quando >= 1:
-- libera se o paciente OU o responsável (quando comenta por ele) tiver ao menos essa idade;
-- sem data de nascimento cadastrada para validar, o comentário é bloqueado.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_numerico)
VALUES ('Idade mínima para comentar na rede social',
        'Idade mínima (em anos) para comentar na rede social. 0 = sem restrição. Sem data de nascimento cadastrada para validação, não é possível comentar.',
        'IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS', 'NUMERICO', 0);
