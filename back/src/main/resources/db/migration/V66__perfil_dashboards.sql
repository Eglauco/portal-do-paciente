-- Dashboards por perfil: a antiga tela única 'DASHBOARD' (que liberava os 5 dashboards de
-- uma vez) vira 5 telas independentes, uma por dashboard. Assim um perfil pode ver só alguns.
-- Todo perfil que já tinha 'DASHBOARD' recebe as 5 novas chaves (preserva o acesso atual).

INSERT INTO perfil_tela (perfil_id, tela)
SELECT pt.perfil_id, novo.tela
FROM perfil_tela pt
CROSS JOIN (VALUES
    ('DASHBOARD_GERAL'), ('DASHBOARD_AGENDAMENTOS'), ('DASHBOARD_CHATS'),
    ('DASHBOARD_SAU'), ('DASHBOARD_NPS')
) AS novo(tela)
WHERE pt.tela = 'DASHBOARD'
ON CONFLICT (perfil_id, tela) DO NOTHING;

-- Remove a chave antiga (o enum Tela não a tem mais; ficaria órfã).
DELETE FROM perfil_tela WHERE tela = 'DASHBOARD';
