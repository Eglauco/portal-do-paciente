-- Senha (hash BCrypt) das contas administrativas, para o login com JWT.
-- O hash da conta admin é semeado no startup (SeedContaAdmin) para garantir
-- compatibilidade com o BCryptPasswordEncoder do Spring Security.

ALTER TABLE usuario ADD COLUMN senha_hash VARCHAR(100);
