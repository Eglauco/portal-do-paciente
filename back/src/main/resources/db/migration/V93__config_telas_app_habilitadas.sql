-- Kill switch global por tela do app (BOOLEANO). LIGADA (padrão) = tela visível para todos;
-- DESLIGADA = a tela some para TODOS os pacientes/responsáveis e some da matriz de liberação
-- (os vínculos por-paciente são preservados; religar a tela restaura tudo). O admin ajusta na
-- tela de Configurações. Uma linha por FuncionalidadeApp togglável (Meu Perfil fica sempre ativo).

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano) VALUES
    ('Tela: Agendamentos (app)',
     'Quando desligada, a tela de Agendamentos fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_AGENDAMENTOS_HABILITADA', 'BOOLEANO', TRUE),
    ('Tela: Chat ao vivo (app)',
     'Quando desligada, a tela de Chat ao vivo fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_CHAT_HABILITADA', 'BOOLEANO', TRUE),
    ('Tela: SAU / Manifestações (app)',
     'Quando desligada, a tela de SAU (Manifestações) fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_SAU_HABILITADA', 'BOOLEANO', TRUE),
    ('Tela: Rede Social (app)',
     'Quando desligada, a tela de Rede Social (Novidades) fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_REDE_SOCIAL_HABILITADA', 'BOOLEANO', TRUE),
    ('Tela: Prontuário (app)',
     'Quando desligada, a tela de Prontuário fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_PRONTUARIO_HABILITADA', 'BOOLEANO', TRUE),
    ('Tela: NPS (app)',
     'Quando desligada, a tela de NPS fica oculta para todos os pacientes e responsáveis no app (e some da liberação). Os vínculos são preservados; religar restaura.',
     'APP_TELA_NPS_HABILITADA', 'BOOLEANO', TRUE);
