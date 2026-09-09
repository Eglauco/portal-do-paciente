-- Data de nascimento do responsável (opcional). Usada para validar a idade mínima ao
-- comentar na rede social (config IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS): um responsável
-- adulto pode comentar pelo paciente mesmo quando o paciente não atinge a idade mínima.
ALTER TABLE responsavel ADD COLUMN data_nascimento DATE;
