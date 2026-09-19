-- Análise de documentos do prontuário por IA: cadastro de Tipo de Documento (com prompts que
-- direcionam a IA), resultado da análise por documento (resumo clínico + status + validação humana),
-- status de alerta do prontuário (rollup, para busca) e o toggle global do recurso.

-- Cadastro de Tipo de Documento do Prontuário. promptResumo vazio = IA não roda; promptValidacao
-- vazio = IA nunca marca "Aguardando validação" (só gera resumo).
CREATE TABLE tipo_documento_prontuario (
    id               BIGSERIAL PRIMARY KEY,
    nome             VARCHAR(120) NOT NULL,
    prompt_resumo    TEXT,
    prompt_validacao TEXT,
    ativo            BOOLEAN NOT NULL DEFAULT TRUE
);
-- Nome único (case-insensitive).
CREATE UNIQUE INDEX uk_tipo_doc_prontuario_nome ON tipo_documento_prontuario (lower(nome));

-- Documento: tipo escolhido no upload + resultado da análise por IA + validação humana.
-- status_analise: NAO_ANALISADO | SEM_ALTERACOES | AGUARDANDO_VALIDACAO | VALIDADO | NAO_ANALISAVEL
ALTER TABLE documento ADD COLUMN tipo_id        BIGINT REFERENCES tipo_documento_prontuario (id);
ALTER TABLE documento ADD COLUMN resumo_clinico TEXT;
ALTER TABLE documento ADD COLUMN status_analise VARCHAR(30) NOT NULL DEFAULT 'NAO_ANALISADO';
ALTER TABLE documento ADD COLUMN validado_por   BIGINT REFERENCES usuario (id);
ALTER TABLE documento ADD COLUMN validado_em    TIMESTAMP;
ALTER TABLE documento ADD COLUMN analisado_em   TIMESTAMP;
CREATE INDEX idx_documento_tipo ON documento (tipo_id);
-- FK de validado_por para usuario: o RESTRICT ao excluir usuário precisa varrer esta coluna.
CREATE INDEX idx_documento_validado_por ON documento (validado_por);

-- Prontuário: status de alerta (rollup dos documentos), para filtrar na busca.
-- SEM_ALTERACOES | AGUARDANDO_VALIDACAO | VALIDADO  ("Alerta" = aguardando ou validado)
ALTER TABLE prontuario ADD COLUMN status_alerta VARCHAR(30) NOT NULL DEFAULT 'SEM_ALTERACOES';

-- Toggle global do recurso (começa DESLIGADO; liga na tela de Configurações).
INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_booleano)
VALUES ('Análise de documentos do prontuário por IA',
        'Quando ligada, ao subir um documento no prontuário a IA gera um resumo clínico e, conforme o '
        || 'prompt do Tipo de Documento, pode marcar o documento como "Aguardando validação" (alerta) e '
        || 'notificar a unidade. Desligada, nada é analisado.',
        'APP_PRONTUARIO_IA_HABILITADO', 'BOOLEANO', FALSE);
