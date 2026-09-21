-- Senha (PIN) de acesso do app na conta (por CPF): permite entrar sem SMS, reduzindo custo. Hash
-- BCrypt; nulo = ainda não definiu (ou foi resetado por um novo OTP). Tentativas para bloquear
-- força bruta. Serve tanto para paciente quanto para responsável (ambos logam por uma conta_app).
ALTER TABLE conta_app ADD COLUMN senha_hash       VARCHAR(100);
ALTER TABLE conta_app ADD COLUMN senha_tentativas INTEGER NOT NULL DEFAULT 0;
