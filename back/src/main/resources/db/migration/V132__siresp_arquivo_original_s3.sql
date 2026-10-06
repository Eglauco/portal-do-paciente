-- Agora guardamos o ARQUIVO XML ORIGINAL (bytes exatos do upload) no S3 (pasta "siresp/") e referenciamos a URL
-- em cada registro do import. É o que reenviamos ao cliente no "Post XML" (sem nenhuma divergência) e o que o
-- botão de download baixa. Substitui o xml_bruto reconstruído por registro — a fonte da verdade passa a ser o
-- objeto no S3 (registros sem URL caem no fallback de reconstrução pelas colunas).
ALTER TABLE siresp ADD COLUMN arquivo_url VARCHAR(512);
ALTER TABLE siresp DROP COLUMN xml_bruto;
