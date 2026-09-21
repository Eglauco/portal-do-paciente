-- Ledger (auditoria) de uso de IA: uma linha por chamada (documento/resumo/chat/moderação), para
-- conferir o gasto com a fatura da plataforma. Append-only; a tela é só-consulta.
CREATE TABLE uso_ia (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(40)  NOT NULL,
    descricao      VARCHAR(200) NOT NULL,
    modelo_ia      VARCHAR(60),
    tokens_entrada BIGINT,
    tokens_saida   BIGINT,
    custo_usd      NUMERIC(12, 6),
    rota           VARCHAR(200),
    criado_em      TIMESTAMP    NOT NULL
);
CREATE INDEX idx_uso_ia_criado_em ON uso_ia (criado_em);
CREATE INDEX idx_uso_ia_tipo ON uso_ia (tipo);

-- Concede a nova tela USO_IA a todo perfil que já tem CONFIGURACOES (dado sensível de custo).
-- O enum Tela é VARCHAR livre. Admin ajusta depois na tela de Perfis.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT DISTINCT pt.perfil_id, 'USO_IA'
FROM perfil_tela pt
WHERE pt.tela = 'CONFIGURACOES'
ON CONFLICT (perfil_id, tela) DO NOTHING;
