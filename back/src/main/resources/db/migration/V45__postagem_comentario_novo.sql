-- Status de "comentário novo" nas postagens (para o admin moderar a Rede Social):
--   ultimo_comentario_paciente_em : atualizado quando um PACIENTE comenta/responde
--   comentarios_vistos_em         : atualizado quando o admin abre a postagem
-- "Novo comentário" = ultimo_comentario_paciente_em > comentarios_vistos_em (ou visto nulo).

ALTER TABLE postagem
    ADD COLUMN ultimo_comentario_paciente_em TIMESTAMP,
    ADD COLUMN comentarios_vistos_em         TIMESTAMP;

-- Backfill: último comentário de PACIENTE por postagem (inclui respostas).
UPDATE postagem p
SET ultimo_comentario_paciente_em = sub.ultimo
FROM (
    SELECT postagem_id, MAX(criado_em) AS ultimo
    FROM comentario
    WHERE paciente_id IS NOT NULL
    GROUP BY postagem_id
) sub
WHERE sub.postagem_id = p.id;

-- Postagens existentes começam como "vistas" agora, evitando marcar tudo como novo
-- de uma vez na estreia da funcionalidade (só comentários futuros disparam o status).
UPDATE postagem SET comentarios_vistos_em = now();
