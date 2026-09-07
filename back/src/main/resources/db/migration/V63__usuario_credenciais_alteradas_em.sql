-- Versionamento do token para invalidar TODAS as sessões ao trocar a senha: marca
-- quando as credenciais foram alteradas pela última vez. Tokens ADMIN emitidos antes
-- deste momento são rejeitados no decode do JWT. Nulo = nunca trocou (nada a invalidar).

ALTER TABLE usuario ADD COLUMN credenciais_alteradas_em TIMESTAMP;
