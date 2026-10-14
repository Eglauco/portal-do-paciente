-- A4 (multi-inquilino): o e-mail do admin passa a ser unico POR INQUILINO (o mesmo e-mail pode
-- administrar clinicas diferentes). O inquilino do login agora vem do SUBDOMINIO (header X-Inquilino
-- resolvido pelo SubdominioTenantFilter), nao mais do e-mail global. Idempotente.
DROP INDEX IF EXISTS public.uk_usuario_login_email;

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuario_login_inq_email
    ON public.usuario_login (inquilino_id, lower(email));
