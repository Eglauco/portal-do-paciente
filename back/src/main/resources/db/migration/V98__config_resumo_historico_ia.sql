-- Resumo do histórico do paciente por IA (tela Prontuário Médico). Agora é gerado AUTOMATICAMENTE
-- ao analisar um documento novo (não há mais botão manual). Este toggle permite DESLIGAR só o resumo
-- (economiza tokens) mantendo a análise por documento. Começa ligado; só atua se a análise de
-- documentos (APP_PRONTUARIO_IA_HABILITADO) também estiver ligada.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano)
VALUES ('Resumo do histórico do paciente por IA',
        'Quando ligada, o resumo geral do histórico do paciente (tela Prontuário Médico) é gerado '
        || 'automaticamente pela IA ao analisar um documento novo. Desligue para economizar tokens '
        || '(a análise por documento continua funcionando). Requer a análise de documentos ligada.',
        'APP_PRONTUARIO_RESUMO_IA_HABILITADO', 'BOOLEANO', TRUE);
