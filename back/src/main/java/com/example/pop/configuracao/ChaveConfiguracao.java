package com.example.pop.configuracao;

/**
 * Chaves de configuração usadas nas regras de negócio — constantes para NÃO usar
 * "string mágica" espalhada pelo código (evita erro de digitação e centraliza).
 *
 * <p>Ao precisar de uma nova configuração:
 * <ol>
 *   <li>Adicione a constante aqui (ex.: {@code TEMPO_PARA_DISPARAR_MENSAGEM = "TEMPO_PARA_DISPARAR_MENSAGEM"}).</li>
 *   <li>Crie uma migration (INSERT em {@code configuracao}) com essa chave, o tipo e um valor padrão.</li>
 *   <li>Na regra, leia via {@code ConfiguracaoService.lerBooleano/lerNumerico/lerTexto(ChaveConfiguracao.X)}.</li>
 * </ol>
 * O admin ajusta o valor depois pela tela de Configurações.
 */
public final class ChaveConfiguracao {

    private ChaveConfiguracao() {
    }

    /** Minutos que o autor tem para editar o próprio comentário na rede social (NUMERICO). */
    public static final String MINUTOS_PARA_EDITAR_COMENTARIO = "MINUTOS_PARA_EDITAR_COMENTARIO";

    /** Mostrar só as iniciais (em vez do nome) do paciente e do responsável na rede social (BOOLEANO). */
    public static final String NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL = "NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL";

    /**
     * Ocultar o nome do usuário (back-office) que comentou na rede social: LIGADA mostra
     * "Administração"; DESLIGADA mostra o nome completo do usuário (BOOLEANO).
     */
    public static final String OCULTAR_NOME_USUARIO_NA_REDESOCIAL = "OCULTAR_NOME_USUARIO_NA_REDESOCIAL";

    /**
     * Idade mínima (anos) para comentar na rede social (NUMERICO). 0 = sem restrição. Quando
     * {@code >= 1}: libera se o paciente OU o responsável (quando comenta por ele) tiver ao
     * menos essa idade; sem data de nascimento para validar → bloqueia.
     */
    public static final String IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS = "IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS";

    /** Cor primária da plataforma (COR, hex {@code #RRGGBB}) — semente do tema do front/app. */
    public static final String COR_PRIMARIA_PLATAFORMA = "COR_PRIMARIA_PLATAFORMA";
}
