-- Tabela de conselhos de classe (ex.: CRM, COREN), cadastro simples id + nome.

CREATE TABLE IF NOT EXISTS conselho (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);

-- Principais conselhos de saúde do Brasil (o admin edita/remove pela tela).
INSERT INTO conselho (nome) VALUES
    ('CRM — Conselho Regional de Medicina'),
    ('COREN — Conselho Regional de Enfermagem'),
    ('CRO — Conselho Regional de Odontologia'),
    ('CRF — Conselho Regional de Farmácia'),
    ('CREFITO — Conselho Regional de Fisioterapia e Terapia Ocupacional'),
    ('CRN — Conselho Regional de Nutrição'),
    ('CRP — Conselho Regional de Psicologia'),
    ('CRFa — Conselho Regional de Fonoaudiologia'),
    ('CRBM — Conselho Regional de Biomedicina'),
    ('CRTR — Conselho Regional de Técnicos em Radiologia');

-- Libera a nova tela 'CONSELHOS' para o perfil "Administrador" (mesma regra do V44).
-- Os demais perfis o admin libera manualmente em Perfis.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT p.id, 'CONSELHOS'
FROM perfil p
WHERE p.nome = 'Administrador'
ON CONFLICT (perfil_id, tela) DO NOTHING;
