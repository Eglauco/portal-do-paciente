-- Dispositivos registrados para notificações push (Expo Push Token).
CREATE TABLE IF NOT EXISTS dispositivo (
    id        BIGSERIAL PRIMARY KEY,
    token     VARCHAR(255) NOT NULL UNIQUE,
    criado_em TIMESTAMP NOT NULL
);
