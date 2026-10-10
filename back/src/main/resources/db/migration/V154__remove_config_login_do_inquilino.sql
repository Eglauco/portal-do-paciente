-- Título, subtítulo e imagem de fundo do login são usados SÓ na tela de login, que é PRÉ-INQUILINO
-- (no login ainda não se sabe qual inquilino vai entrar). Logo vêm SEMPRE da Configuração da
-- Plataforma (public.configuracao_plataforma, gerida pelo super-admin), nunca do inquilino.
-- Remove essas 3 chaves da configuração POR-INQUILINO para não poluir a tela de Configurações do admin.
--
-- Roda em cada schema de inquilino (db/migration). NOME_PLATAFORMA e LOGO_PLATAFORMA PERMANECEM
-- por-inquilino (aparecem DENTRO do sistema após o login: sidebar, abas, favicon, PDF).
DELETE FROM configuracao WHERE chave IN ('LOGIN_TITULO', 'LOGIN_SUBTITULO', 'LOGIN_FUNDO');
