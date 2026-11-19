-- Senha do paciente deixa de ser PIN de 6 digitos e passa a ser LIVRE (minimo 8 caracteres).
-- Decisao do dono (19/11/2026): INVALIDAR todos os PINs existentes. Quem tinha PIN volta ao
-- "Primeiro acesso" (pre-checagem passa a responder temSenha=false) -> SMS -> define a senha nova.
-- Zera tambem tentativas e lockout. Roda uma unica vez (Flyway versionado); nao apaga a identidade.
UPDATE public.paciente_identidade
   SET senha_hash       = NULL,
       senha_tentativas = 0,
       bloqueado_ate    = NULL,
       atualizado_em    = now()
 WHERE senha_hash IS NOT NULL
    OR senha_tentativas <> 0
    OR bloqueado_ate IS NOT NULL;
