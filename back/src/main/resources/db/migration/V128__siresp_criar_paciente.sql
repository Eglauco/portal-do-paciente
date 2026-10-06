-- Novo parâmetro: criar o paciente quando não encontrado (por código de integração ou CPF) na importação do SIRESP.
-- Começa DESLIGADO: nada é criado até o admin ligar no modal.
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano) VALUES
  ('SIRESP — Criar paciente', 'Cria o paciente completo com os dados do XML quando não encontrado na importação do SIRESP.',
   'SIRESP_CRIAR_PACIENTE', 'BOOLEANO', false);
