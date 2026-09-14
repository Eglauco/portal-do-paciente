-- White-label: textos de marca configuráveis. Semeados com os valores HARDCODED de hoje,
-- então nada muda visualmente até o admin editar. Lidos publicamente em GET /marca (login).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto)
VALUES ('Nome da plataforma',
        'Nome exibido no login, na tela de trocar unidade, no rodapé da sidebar, no título das abas do navegador e no rodapé dos relatórios (PDF).',
        'NOME_PLATAFORMA', 'TEXTO', 'Portal do Paciente');

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto)
VALUES ('Título do login',
        'Título grande do painel de marca na tela de login.',
        'LOGIN_TITULO', 'TEXTO', 'A gestão do cuidado começa aqui.');

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_texto)
VALUES ('Subtítulo do login',
        'Texto de apoio abaixo do título, na tela de login.',
        'LOGIN_SUBTITULO', 'TEXTO',
        'Cadastre e acompanhe exames, consultas e informações dos pacientes — com segurança e agilidade no dia a dia da equipe.');
