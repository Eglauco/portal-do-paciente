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

    /**
     * Nome da plataforma (TEXTO) — white-label. Exibido no login, na tela de trocar unidade,
     * no rodapé da sidebar, no título das abas do navegador e no rodapé dos relatórios (PDF).
     */
    public static final String NOME_PLATAFORMA = "NOME_PLATAFORMA";

    /** Título grande do painel de marca da tela de login (TEXTO) — white-label. */
    public static final String LOGIN_TITULO = "LOGIN_TITULO";

    /** Subtítulo (texto de apoio) abaixo do título, na tela de login (TEXTO) — white-label. */
    public static final String LOGIN_SUBTITULO = "LOGIN_SUBTITULO";

    /**
     * Logomarca da plataforma (IMAGEM, URL no S3) — white-label. Substitui o SVG fixo no
     * login, na tela de trocar unidade, na sidebar, no favicon e no cabeçalho dos PDFs.
     */
    public static final String LOGO_PLATAFORMA = "LOGO_PLATAFORMA";

    /**
     * Imagem de fundo do painel azul do login/trocar-unidade (IMAGEM, URL no S3) — white-label.
     * Fica atrás do azul (que cobre em opacidade alta), como textura sutil.
     */
    public static final String LOGIN_FUNDO = "LOGIN_FUNDO";

    // ---------- Habilitação global de telas do app (kill switch por tela; BOOLEANO, TRUE = visível) ----------
    // Quando DESLIGADA, a tela some para TODOS os pacientes/responsáveis e a liberação dela some da matriz
    // (os vínculos são preservados: religar a tela restaura tudo). Uma chave por FuncionalidadeApp togglável.

    /** Tela de Agendamentos do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_AGENDAMENTOS_HABILITADA = "APP_TELA_AGENDAMENTOS_HABILITADA";

    /** Tela de Chat ao vivo do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_CHAT_HABILITADA = "APP_TELA_CHAT_HABILITADA";

    /** Tela de SAU (Manifestações) do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_SAU_HABILITADA = "APP_TELA_SAU_HABILITADA";

    /** Tela de Rede Social (Novidades) do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_REDE_SOCIAL_HABILITADA = "APP_TELA_REDE_SOCIAL_HABILITADA";

    /** Tela de Prontuário do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_PRONTUARIO_HABILITADA = "APP_TELA_PRONTUARIO_HABILITADA";

    /** Tela de NPS do app habilitada globalmente (BOOLEANO). */
    public static final String APP_TELA_NPS_HABILITADA = "APP_TELA_NPS_HABILITADA";

    // ---------- Chat ao vivo com IA ----------

    /**
     * Liga/desliga GLOBAL do primeiro atendimento por IA no chat ao vivo (BOOLEANO). Ligada = a
     * assistente virtual responde o paciente até um humano assumir; desligada = chat vai direto
     * para a fila humana. Começa desligada (semeada em V94).
     */
    public static final String APP_CHAT_IA_HABILITADO = "APP_CHAT_IA_HABILITADO";

    // ---------- Análise de documentos do prontuário por IA ----------

    /**
     * Liga/desliga GLOBAL da análise de documentos do prontuário por IA (BOOLEANO). Ligada = ao
     * subir um documento a IA gera resumo clínico e pode marcar "Aguardando validação" conforme o
     * Tipo de Documento; desligada = nada é analisado. Começa desligada (semeada em V96).
     */
    public static final String APP_PRONTUARIO_IA_HABILITADO = "APP_PRONTUARIO_IA_HABILITADO";

    /**
     * Liga/desliga o RESUMO do histórico por IA (tela Prontuário Médico), gerado automaticamente ao
     * analisar um documento novo (BOOLEANO). Desligar economiza tokens mantendo a análise por
     * documento. Só atua com {@link #APP_PRONTUARIO_IA_HABILITADO} ligado. Começa ligada (V98).
     */
    public static final String APP_PRONTUARIO_RESUMO_IA_HABILITADO = "APP_PRONTUARIO_RESUMO_IA_HABILITADO";

    // ---------- Custo dos modelos de IA (NUMERICO, US$ por milhão de tokens / MTok) ----------
    // Usados para calcular e gravar o custo de cada uso de IA (prontuário, chat, moderação).

    /** Preço dos tokens de ENTRADA do Claude Opus 5, em US$ por MTok (NUMERICO). */
    public static final String CUSTO_IA_OPUS_5_ENTRADA_USD_MTOK = "CUSTO_IA_OPUS_5_ENTRADA_USD_MTOK";
    /** Preço dos tokens de SAÍDA do Claude Opus 5, em US$ por MTok (NUMERICO). */
    public static final String CUSTO_IA_OPUS_5_SAIDA_USD_MTOK = "CUSTO_IA_OPUS_5_SAIDA_USD_MTOK";
    /** Preço dos tokens de ENTRADA do Claude Haiku 4.5, em US$ por MTok (NUMERICO). */
    public static final String CUSTO_IA_HAIKU_45_ENTRADA_USD_MTOK = "CUSTO_IA_HAIKU_45_ENTRADA_USD_MTOK";
    /** Preço dos tokens de SAÍDA do Claude Haiku 4.5, em US$ por MTok (NUMERICO). */
    public static final String CUSTO_IA_HAIKU_45_SAIDA_USD_MTOK = "CUSTO_IA_HAIKU_45_SAIDA_USD_MTOK";

    // ---------- Assinatura eletrônica (TCLE) ----------

    /**
     * Provedor de assinatura eletrônica dos termos (TEXTO): {@code ZAPSIGN}, {@code AUTENTIQUE},
     * {@code CLICKSIGN} ou {@code DOCUSIGN}. As credenciais de cada provedor ficam em variáveis de ambiente;
     * aqui só se ESCOLHE qual usar. Semeada em V112 com {@code ZAPSIGN} (retrocompatibilidade).
     */
    public static final String PROVEDOR_ASSINATURA = "PROVEDOR_ASSINATURA";

    /**
     * Modo de assinatura em lote quando o provedor é AUTENTIQUE (TEXTO): {@code SEPARADO} (um documento e
     * um PDF assinado por termo) ou {@code COMBINADO} (todos os termos num único PDF/uma assinatura). Não
     * afeta a ZapSign (que sempre agrupa numa cerimônia só, mantendo um PDF por termo). Semeada em V112.
     */
    public static final String AUTENTIQUE_MODO_LOTE = "AUTENTIQUE_MODO_LOTE";

    /**
     * Modo de assinatura em lote quando o provedor é CLICKSIGN (TEXTO): {@code SEPARADO} (um documento por
     * termo dentro do mesmo envelope) ou {@code COMBINADO} (todos os termos num único PDF). Semeada em V113.
     */
    public static final String CLICKSIGN_MODO_LOTE = "CLICKSIGN_MODO_LOTE";

    /**
     * Modo de assinatura em lote quando o provedor é DOCUSIGN (TEXTO): {@code SEPARADO} (um documento por
     * termo dentro do mesmo envelope/cerimônia, um PDF assinado por termo) ou {@code COMBINADO} (todos os
     * termos num único PDF). Semeada em V114.
     */
    public static final String DOCUSIGN_MODO_LOTE = "DOCUSIGN_MODO_LOTE";

    // ---------- Credenciais dos provedores de assinatura (self-service pela tela "Provedores de assinatura") ----------
    // Os tokens/segredos são do tipo SEGREDO (cifrados no banco, write-only). O AMBIENTE (TEXTO) = SANDBOX|PRODUCAO
    // e resolve a base URL internamente (o cliente escolhe o ambiente, não digita URL). Semeadas em V122 (vazias).

    /** ZapSign — Token de API (SEGREDO). */
    public static final String ZAPSIGN_API_TOKEN = "ZAPSIGN_API_TOKEN";
    /** ZapSign — Segredo do webhook (SEGREDO; header X-Zapsign-Secret que registramos no painel da ZapSign). */
    public static final String ZAPSIGN_WEBHOOK_SECRET = "ZAPSIGN_WEBHOOK_SECRET";
    /** ZapSign — Ambiente ATIVO (TEXTO): {@code SANDBOX} ou {@code PRODUCAO} (escolhe qual URL abaixo é usada). */
    public static final String ZAPSIGN_AMBIENTE = "ZAPSIGN_AMBIENTE";
    /** ZapSign — URL base de SANDBOX (TEXTO editável; o provedor pode mudar). */
    public static final String ZAPSIGN_URL_SANDBOX = "ZAPSIGN_URL_SANDBOX";
    /** ZapSign — URL base de PRODUÇÃO (TEXTO editável). */
    public static final String ZAPSIGN_URL_PRODUCAO = "ZAPSIGN_URL_PRODUCAO";

    /** Autentique — Token de API (SEGREDO). */
    public static final String AUTENTIQUE_API_TOKEN = "AUTENTIQUE_API_TOKEN";
    /** Autentique — Segredo do webhook (SEGREDO; valida o HMAC do X-Autentique-Signature). */
    public static final String AUTENTIQUE_WEBHOOK_SECRET = "AUTENTIQUE_WEBHOOK_SECRET";
    /** Autentique — Ambiente ATIVO (TEXTO): {@code SANDBOX} (documentos sem validade jurídica) ou {@code PRODUCAO}. */
    public static final String AUTENTIQUE_AMBIENTE = "AUTENTIQUE_AMBIENTE";
    /** Autentique — URL base de SANDBOX (TEXTO editável). */
    public static final String AUTENTIQUE_URL_SANDBOX = "AUTENTIQUE_URL_SANDBOX";
    /** Autentique — URL base de PRODUÇÃO (TEXTO editável). */
    public static final String AUTENTIQUE_URL_PRODUCAO = "AUTENTIQUE_URL_PRODUCAO";

    /** Clicksign — Access token (SEGREDO). */
    public static final String CLICKSIGN_ACCESS_TOKEN = "CLICKSIGN_ACCESS_TOKEN";
    /** Clicksign — Segredo do webhook (SEGREDO; valida o HMAC do header Content-Hmac). */
    public static final String CLICKSIGN_WEBHOOK_SECRET = "CLICKSIGN_WEBHOOK_SECRET";
    /** Clicksign — Ambiente ATIVO (TEXTO): {@code SANDBOX} ou {@code PRODUCAO} (escolhe qual URL abaixo é usada; o host do widget deriva dela). */
    public static final String CLICKSIGN_AMBIENTE = "CLICKSIGN_AMBIENTE";
    /** Clicksign — URL base de SANDBOX (TEXTO editável). */
    public static final String CLICKSIGN_URL_SANDBOX = "CLICKSIGN_URL_SANDBOX";
    /** Clicksign — URL base de PRODUÇÃO (TEXTO editável). */
    public static final String CLICKSIGN_URL_PRODUCAO = "CLICKSIGN_URL_PRODUCAO";
    /**
     * Clicksign — Autenticação do signatário no Widget Embedded (TEXTO): {@code sms} (padrão), {@code email} ou
     * {@code whatsapp}. Token enviado ao celular/e-mail do signatário; a tela do Clicksign conduz a verificação.
     */
    public static final String CLICKSIGN_AUTENTICACAO = "CLICKSIGN_AUTENTICACAO";

    /** SIRESP — Liga/desliga geral da atualização do cadastro do paciente na importação do XML (BOOLEANO). */
    public static final String SIRESP_ATUALIZAR_PACIENTE = "SIRESP_ATUALIZAR_PACIENTE";
    /** SIRESP — Criar o paciente quando não encontrado (por código de integração / CPF) na importação (BOOLEANO). */
    public static final String SIRESP_CRIAR_PACIENTE = "SIRESP_CRIAR_PACIENTE";
    /**
     * SIRESP — URL do Sistema de Gestão que recebe o XML via HTTP POST (TEXTO), replicando o "Post XML" do SIRESP
     * (parâmetro de formulário {@code msg} com o XML inteiro). Vazia = envio desabilitado.
     */
    public static final String SIRESP_POST_URL = "SIRESP_POST_URL";
    /**
     * SIRESP — Enviar o XML ao Sistema de Gestão automaticamente ao importar (BOOLEANO). Ligado: logo após o upload,
     * o arquivo original é reenviado ao Sistema de Gestão (só se houver {@link #SIRESP_POST_URL}). Desligado: só importa.
     */
    public static final String SIRESP_ENVIAR_AO_IMPORTAR = "SIRESP_ENVIAR_AO_IMPORTAR";
}
