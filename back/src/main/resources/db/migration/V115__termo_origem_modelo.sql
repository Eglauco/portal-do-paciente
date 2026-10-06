-- Origem do modelo do termo (TCLE): arquivo .docx nosso (atual) ou modelo já pronto no ZapSign.

-- Origem: ARQUIVO (padrão, retrocompatível) ou ZAPSIGN_MODELO.
ALTER TABLE termo_procedimento
    ADD COLUMN origem_modelo VARCHAR(30) NOT NULL DEFAULT 'ARQUIVO';

-- Nome do modelo do ZapSign selecionado (exibição no back-office).
ALTER TABLE termo_procedimento
    ADD COLUMN modelo_provider_nome VARCHAR(200);

-- Em ZAPSIGN_MODELO não há arquivo nosso: a URL passa a ser opcional.
ALTER TABLE termo_procedimento
    ALTER COLUMN url DROP NOT NULL;
