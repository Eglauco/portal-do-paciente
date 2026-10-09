-- Multi-inquilino (Fase 2.5, parcial): roteamento dos WEBHOOKS de assinatura. Mapeia a CHAVE do documento
-- no provedor -> schema do inquilino, para o webhook (que chega SEM JWT) achar o inquilino dono do termo.
-- Vive SEMPRE no public (tabela de PLATAFORMA). Escrita ao criar a cerimonia (paciente logado, tenant setado).
-- Por ora so a ESCRITA + a guarda anti-crash nos controllers; a LEITURA no webhook (resolver o tenant pela
-- chave) fica para o build completo por-inquilino dos webhooks (ver memoria assinatura-webhooks-multi-inquilino).
CREATE TABLE IF NOT EXISTS public.assinatura_roteamento (
    id           BIGSERIAL    PRIMARY KEY,
    chave        VARCHAR(160) NOT NULL,
    schema_name  VARCHAR(63)  NOT NULL,
    criado_em    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assinatura_roteamento_chave ON public.assinatura_roteamento (chave);
