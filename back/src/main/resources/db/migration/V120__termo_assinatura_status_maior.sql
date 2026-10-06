-- O novo status AGUARDANDO_PROFISSIONAL (23 chars) não cabia em varchar(20). Alarga a coluna.
ALTER TABLE termo_assinatura
    ALTER COLUMN status TYPE VARCHAR(30);
