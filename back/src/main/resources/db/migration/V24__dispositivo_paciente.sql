-- Vincula o token de push (Expo) ao paciente dono, para notificar SÓ o aparelho
-- dele em eventos privados (chat, NPS, prontuário, agendamento). Postagem segue broadcast.
ALTER TABLE dispositivo ADD COLUMN paciente_id BIGINT;

ALTER TABLE dispositivo
    ADD CONSTRAINT fk_dispositivo_paciente
    FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE SET NULL;

CREATE INDEX idx_dispositivo_paciente ON dispositivo (paciente_id);
