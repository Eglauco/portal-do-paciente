-- Regras de atualização do cadastro do paciente a partir do XML do SIRESP (Fase 2 das regras de negócio).
-- Ação por campo: SEMPRE | SE_VAZIO | NUNCA (padrão NUNCA = não mexe). O liga/desliga geral é uma config BOOLEANO.
CREATE TABLE siresp_config_campo (
    id    BIGSERIAL PRIMARY KEY,
    campo VARCHAR(40) NOT NULL UNIQUE,
    acao  VARCHAR(10) NOT NULL DEFAULT 'NUNCA'
);

INSERT INTO siresp_config_campo (campo, acao) VALUES
  ('nome', 'NUNCA'), ('sexo', 'NUNCA'), ('dataNascimento', 'NUNCA'), ('rg', 'NUNCA'), ('cpf', 'NUNCA'),
  ('nomeMae', 'NUNCA'), ('nomePai', 'NUNCA'), ('rua', 'NUNCA'), ('numero', 'NUNCA'), ('bairro', 'NUNCA'),
  ('municipio', 'NUNCA'), ('uf', 'NUNCA'), ('cep', 'NUNCA'), ('email', 'NUNCA'), ('cns', 'NUNCA'), ('prontuario', 'NUNCA');

-- Liga/desliga geral (começa DESLIGADO: nada acontece até o admin configurar e ligar no modal).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano) VALUES
  ('SIRESP — Atualizar paciente', 'Liga/desliga a atualização do cadastro do paciente na importação do XML do SIRESP.',
   'SIRESP_ATUALIZAR_PACIENTE', 'BOOLEANO', false);
