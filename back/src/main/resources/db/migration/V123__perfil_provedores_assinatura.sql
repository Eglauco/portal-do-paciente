-- Concede a tela PROVEDORES_ASSINATURA a todo perfil que já libera Configurações (uso imediato; o admin
-- ajusta depois na tela de Perfis). O enum Tela é VARCHAR livre — sem migration de schema.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT DISTINCT pt.perfil_id, 'PROVEDORES_ASSINATURA'
FROM perfil_tela pt
WHERE pt.tela = 'CONFIGURACOES'
ON CONFLICT (perfil_id, tela) DO NOTHING;
