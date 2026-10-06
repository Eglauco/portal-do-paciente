package com.example.pop.assinatura;

import java.util.List;
import java.util.Map;

/**
 * Abstração de um provedor de assinatura eletrônica (ZapSign, Autentique, ...). Esconde as diferenças
 * de cada API (REST x GraphQL, modelo de variáveis, lote, webhook) atrás de uma interface estável, para
 * as regras internas (cadastro de modelos, disparo na presença, confirmação) NÃO dependerem do provedor.
 * O provedor ativo é escolhido em runtime por {@link AssinaturaProviderFactory} (Configuração).
 */
public interface AssinaturaProvider {

    enum TipoEvento { ASSINADO, RECUSADO, OUTRO }

    /**
     * Signatário da cerimônia. {@code email} é opcional e serve para PRÉ-PREENCHER a cerimônia em provedores que
     * suportam (ex.: Autentique, via delivery_method LINK — sem disparar e-mail). Provedores que não usam ignoram.
     */
    record Signatario(String nome, String cpf, String phoneCountry, String phoneNumber, String email,
            String externalId) {
    }

    /** Um termo a assinar, já com as variáveis resolvidas e o modelo (DOCX) de origem. */
    record TermoParaAssinar(Long termoId, String nome, String modeloUrl, String modeloContentType,
            String providerTemplateToken, Map<String, String> variaveis) {
    }

    /** Um documento remoto criado para assinar: cobre 1+ termos e traz o link da cerimônia. */
    record DocumentoAssinatura(List<Long> termoIds, String providerDocToken, String providerSignerId, String signUrl) {
    }

    /** Um arquivo assinado baixado do provedor, pronto para guardar no S3 do POP. */
    record ArquivoAssinado(String providerDocToken, byte[] pdf) {
    }

    /** Coassinante adicionado a um documento já criado: id do signatário + link da cerimônia dele. */
    record Coassinante(String providerSignerId, String signUrl) {
    }

    /** Evento de webhook já validado (autenticidade) e normalizado (tipo + documentos afetados). */
    record EventoWebhook(boolean autentico, TipoEvento tipo, List<String> docTokens) {
    }

    /** Resultado de um teste de conexão com o provedor (tela de Provedores: ✅/❌ + mensagem). */
    record ResultadoTeste(boolean ok, String mensagem) {
    }

    /** Mensagem amigável a partir de um erro de teste de conexão (usa o reason do ResponseStatusException). */
    static String mensagemErro(RuntimeException e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** Identificador do provedor. */
    ProvedorAssinatura id();

    /** true se as credenciais estão configuradas (token etc.) — senão as operações falham em 503. */
    boolean disponivel();

    /**
     * Testa as credenciais com uma chamada autenticada leve (para o botão "Testar conexão"). Default: só checa
     * se está configurado; os provedores que têm uma chamada barata sobrescrevem para validar o token de fato.
     */
    default ResultadoTeste testarConexao() {
        return disponivel() ? new ResultadoTeste(true, "Credenciais configuradas.")
                : new ResultadoTeste(false, "Credenciais não configuradas.");
    }

    /** true se usa um MODELO remoto registrado (ZapSign); false se renderiza o documento local (Autentique). */
    boolean usaModeloRemoto();

    /** Registra o modelo (DOCX) no provedor e devolve o token do template remoto; null se renderiza local. */
    String registrarModelo(String nome, byte[] docxBytes);

    /**
     * Cria os documentos de assinatura para os termos (variáveis já resolvidas). Encapsula o LOTE: cada
     * provedor decide como agrupar. {@code combinar} = juntar tudo num único documento (opção da Autentique).
     * Devolve um {@link DocumentoAssinatura} por documento remoto, cada um cobrindo os termos que agrupa.
     */
    List<DocumentoAssinatura> criar(List<TermoParaAssinar> termos, Signatario signatario, boolean combinar);

    /**
     * Dado o token de documento que veio no webhook, devolve TODOS os arquivos assinados relacionados
     * (na ZapSign: o principal + os extras do lote; na Autentique: o próprio documento). Baixa os binários.
     */
    List<ArquivoAssinado> coletarAssinados(String webhookDocToken);

    /** Valida a autenticidade do webhook (cada provedor do seu jeito) e normaliza o evento. */
    EventoWebhook parseWebhook(Map<String, String> headers, String rawBody);

    // ---------- Coassinatura do profissional (2º signatário, após o paciente) ----------

    /** true se o provedor suporta adicionar um 2º signatário (coassinatura) — ZapSign/DocuSign/Autentique. */
    default boolean suportaCoassinatura() {
        return false;
    }

    /** true se o provedor suporta a coassinatura COM certificado digital (ICP/qualificada) — só ZapSign por ora. */
    default boolean suportaCoassinaturaCertificado() {
        return false;
    }

    /**
     * true se a cerimônia do 2º signatário pode ser EMBUTIDA num {@code <iframe>} no front; false = o front
     * deve abrir em ABA nova (ex.: DocuSign, cuja recipient view é de uso único e depende de headers de frame).
     */
    default boolean cerimoniaCoassinaturaEmbutivel() {
        return true;
    }

    /**
     * Adiciona um 2º signatário (o profissional) ao documento JÁ criado (assinado pelo paciente depois) e
     * devolve o id + o link da cerimônia dele. A ordem (paciente→profissional) é garantida pela máquina de
     * estados do POP (o link do profissional só é exposto após o paciente assinar). {@code usarCertificado} =
     * assinatura QUALIFICADA por certificado digital (ICP); senão, assinatura em tela (avançada).
     */
    default Coassinante adicionarCoassinante(String providerDocToken, Signatario coSignatario,
            boolean usarCertificado) {
        throw new UnsupportedOperationException("Coassinatura não suportada por este provedor.");
    }

    /** true se o signatário indicado JÁ concluiu a assinatura no documento (checagem por-signatário). */
    default boolean signatarioConcluiu(String providerDocToken, String providerSignerId) {
        throw new UnsupportedOperationException("Checagem por signatário não suportada por este provedor.");
    }

    /**
     * URL da cerimônia do 2º signatário (profissional) para abrir AGORA. Provedores de URL DURÁVEL
     * (ZapSign/Autentique) devolvem a {@code signUrlArmazenada}; provedores de URL EFÊMERA (DocuSign) GERAM
     * uma nova sob demanda (uso único, expira em minutos). Só é chamado depois que o paciente já assinou.
     */
    default String urlCoassinante(String providerDocToken, String providerSignerIdProfissional,
            Signatario coSignatario, String signUrlArmazenada) {
        return signUrlArmazenada;
    }

    /**
     * Regenera a URL da cerimônia do PACIENTE após o documento mudar por causa da coassinatura. Necessário no
     * DocuSign: adicionar o 2º signatário INVALIDA a recipient view do paciente já gerada (a cerimônia abriria
     * "sessão encerrada"). Default: devolve a URL atual (provedores de URL durável não precisam regenerar).
     */
    default String regenerarUrlPaciente(String providerDocToken, Signatario paciente, String signUrlAtual) {
        return signUrlAtual;
    }

    /**
     * Finaliza a criação da cerimônia APÓS a etapa de coassinatura (quando houver). Usado pelo Clicksign, que
     * ADIA a ativação do envelope: ele cria tudo em {@code draft}, o profissional é adicionado ainda em draft e
     * só aqui o envelope vira {@code running} (o v3 não deixa adicionar signatário num envelope já running).
     * Default no-op: os demais provedores já finalizam dentro do {@code criar}. Chamado 1x por cerimônia.
     */
    default void finalizarCriacao(String providerDocToken) {
    }
}
