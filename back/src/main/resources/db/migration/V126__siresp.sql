-- Registros importados do SIRESP/CROSS (Módulo de Regulação Ambulatorial): uma linha por <Mensagem> do XML
-- (layout "agendamento de consulta, sob demanda"). Cada campo do XML em sua coluna, como texto cru. Apenas
-- população via upload de XML — sem gerar agendamento e sem regras de negócio (vêm depois). Só-leitura na tela.
CREATE TABLE siresp (
    id                         BIGSERIAL PRIMARY KEY,

    -- Metadados da importação (não vêm do XML)
    unidade_saude_id           BIGINT,
    arquivo                    VARCHAR(255),
    importado_em               TIMESTAMP NOT NULL,
    importado_por_usuario_id   BIGINT,
    importado_por_nome         VARCHAR(150),

    -- Campos do XML (NewDataSet > Mensagem)
    tipo_consulta              TEXT,
    cod_unidade_executante     TEXT,
    id_age_consulta_hor        TEXT,
    id_age_consulta            TEXT,
    age_consulta_nome          TEXT,
    id_especialidade           TEXT,
    nome_especialidade         TEXT,
    cod_dia                    TEXT,
    data_agenda                TEXT,
    hor_ini                    TEXT,
    hor_fim                    TEXT,
    tipo                       TEXT,
    id_motivo                  TEXT,
    id_profissional            TEXT,
    doc_profissional           TEXT,
    origem                     TEXT,
    nome_profissional          TEXT,
    id_protocolo               TEXT,
    subcateg                   TEXT,
    nome_protocolo             TEXT,
    cod_unidade_solicitante    TEXT,
    nome_unidade_solicitante   TEXT,
    cnes_unidade_solicitante   TEXT,
    nome_usuario_solicitante   TEXT,
    dt_ultima_atualiz          TEXT,
    cod_paciente               TEXT,
    nome_paciente              TEXT,
    sexo                       TEXT,
    dt_nascimento              TEXT,
    rg                         TEXT,
    cpf                        TEXT,
    nome_mae                   TEXT,
    nome_pai                   TEXT,
    endereco                   TEXT,
    endereco_numero            TEXT,
    bairro                     TEXT,
    municipio                  TEXT,
    uf                         TEXT,
    cep                        TEXT,
    tel_res_ddd                TEXT,
    tel_res                    TEXT,
    tel_celular_ddd            TEXT,
    tel_celular                TEXT,
    tel_com_ddd                TEXT,
    tel_com                    TEXT,
    tel_com_ramal              TEXT,
    email                      TEXT,
    contato_nome               TEXT,
    contato_tel_ddd            TEXT,
    contato_tel                TEXT,
    num_cns                    TEXT,
    num_prontuario             TEXT
);
CREATE INDEX idx_siresp_unidade_importado ON siresp (unidade_saude_id, importado_em);

-- Libera a tela SIRESP para o perfil Administrador (semeado na V44). O admin ajusta os demais perfis na tela de Perfis.
INSERT INTO perfil_tela (perfil_id, tela)
SELECT p.id, 'SIRESP' FROM perfil p WHERE p.nome = 'Administrador'
ON CONFLICT (perfil_id, tela) DO NOTHING;
