-- E-mail é a chave de login: garante unicidade case-insensitive no banco,
-- fechando a corrida da checagem em nível de aplicação (existsByEmailIgnoreCase).

-- Antes de criar o índice, resolve eventuais duplicados pré-existentes
-- (dados de teste/demonstração): mantém o de menor id e torna os demais únicos.
UPDATE usuario u
SET email = u.email || '+' || u.id
WHERE EXISTS (
    SELECT 1 FROM usuario o
    WHERE lower(o.email) = lower(u.email)
      AND o.id < u.id
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuario_email_lower ON usuario (lower(email));
