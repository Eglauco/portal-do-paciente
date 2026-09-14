-- Novo tipo de configuração IMAGEM (guarda a URL do objeto no S3, pasta "configuracao").
-- Semente da logomarca white-label: valor NULL => o front/PDF caem no SVG fixo até o admin subir uma logo.
ALTER TABLE configuracao ADD COLUMN valor_imagem TEXT;

INSERT INTO configuracao (nome, descricao, chave, tipo_configuracao, valor_imagem)
VALUES ('Logomarca da plataforma',
        'Logo exibida no login, na tela de trocar unidade, na sidebar, no favicon e no cabeçalho dos relatórios (PDF). Sem imagem, usa a logo padrão.',
        'LOGO_PLATAFORMA', 'IMAGEM', NULL);
