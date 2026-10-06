package com.example.pop.docusign;

import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Cliente da API do DocuSign (eSignature REST v2.1) com autenticação JWT Grant. Fluxo: assina um JWT
 * (RS256, com a chave privada RSA) → troca por um access token no OAuth ({@code account-d.docusign.com}) →
 * usa o token nas chamadas REST na conta ({@code demo.docusign.net}). Cria o envelope com o(s) documento(s)
 * pronto(s) (o POP renderiza o .docx), abre a cerimônia embutida (recipient view) para o WebView do app,
 * consulta o status e baixa o(s) PDF(s) assinado(s). Sem token/chave → falha em 503.
 *
 * <p>O JWT usa o {@code Nimbus} (já no classpath via oauth2-resource-server) — sem dependência nova.
 * A chave privada é lida em PKCS#8 (base64 do DER, {@code docusign.private-key-base64}) ou de um arquivo PEM
 * PKCS#8 ({@code docusign.private-key-path}). Consentimento (uma vez) é pré-requisito do JWT Grant.
 */
@Component
public class DocusignClient {

    /** Âncora INVISÍVEL injetada no .docx para posicionar o campo de assinatura do PACIENTE (anchorString). */
    static final String ANCORA_ASSINATURA = "\\ds_sig\\";

    /** Âncora INVISÍVEL do 2º signatário (PROFISSIONAL) — distinta da do paciente para os campos não se sobreporem. */
    static final String ANCORA_ASSINATURA_PROFISSIONAL = "\\ds_sig_prof\\";

    private final String oauthBase;      // https://account-d.docusign.com (demo)
    private final String accountBaseUri; // https://demo.docusign.net
    private final String integrationKey; // client_id (iss)
    private final String userId;         // usuário representado (sub)
    private final String accountId;      // API Account ID (GUID) das chamadas REST
    private final String scope;          // "signature impersonation"
    private final String retornoPageBase;
    private final String webhookUrl;     // se vazio, não anexa eventNotification (Connect)
    private final int connectTimeout;
    private final int readTimeout;
    private final List<String> frameAncestors; // origens que podem embutir a recipient view (iframe do front)
    private final List<String> messageOrigins; // origens das mensagens do DocuSign p/ a janela pai

    private final PrivateKey privateKey; // null quando não configurada
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    // Cache do access token (o JWT Grant devolve ~1h).
    private volatile String accessToken;
    private volatile Instant accessTokenExpira = Instant.EPOCH;

    public DocusignClient(
            @Value("${docusign.oauth-base:https://account-d.docusign.com}") String oauthBase,
            @Value("${docusign.account-base-uri:}") String accountBaseUri,
            @Value("${docusign.integration-key:}") String integrationKey,
            @Value("${docusign.user-id:}") String userId,
            @Value("${docusign.account-id:}") String accountId,
            @Value("${docusign.scope:signature impersonation}") String scope,
            @Value("${docusign.private-key-base64:}") String privateKeyBase64,
            @Value("${docusign.private-key-path:}") String privateKeyPath,
            @Value("${docusign.retorno-page-base:http://localhost:8080}") String retornoPageBase,
            @Value("${docusign.webhook-url:}") String webhookUrl,
            @Value("${docusign.frame-ancestors:}") String frameAncestors,
            @Value("${docusign.message-origins:https://apps-d.docusign.com}") String messageOrigins,
            @Value("${docusign.connect-timeout-segundos:10}") int connectTimeout,
            @Value("${docusign.read-timeout-segundos:60}") int readTimeout) {
        this.oauthBase = semBarra(oauthBase);
        this.accountBaseUri = semBarra(accountBaseUri);
        this.integrationKey = trim(integrationKey);
        this.userId = trim(userId);
        this.accountId = trim(accountId);
        this.scope = scope == null || scope.isBlank() ? "signature impersonation" : scope.trim();
        this.retornoPageBase = semBarra(retornoPageBase);
        this.webhookUrl = trim(webhookUrl);
        this.frameAncestors = listaCsv(frameAncestors);
        this.messageOrigins = listaCsv(messageOrigins);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.privateKey = carregarChave(privateKeyBase64, privateKeyPath);
    }

    /** true quando há credenciais suficientes (chave + ids + base da conta) para operar. */
    public boolean temCredenciais() {
        return privateKey != null && !integrationKey.isEmpty() && !userId.isEmpty()
                && !accountId.isEmpty() && !accountBaseUri.isEmpty();
    }

    /** Documento a enviar no envelope (arquivo já pronto). O {@code documentId} é "1".."N". */
    public record DocumentoEnvio(String documentId, String filename, byte[] conteudo) {
    }

    /** Cria o envelope (status=sent) com os documentos e o signatário embutido; devolve o envelopeId. */
    public String criarEnvelope(String assunto, List<DocumentoEnvio> docs, AssinaturaProvider.Signatario s) {
        exigirConfig();
        ObjectNode raiz = mapper.createObjectNode();
        raiz.put("emailSubject", assunto == null || assunto.isBlank() ? "Termos de Consentimento" : assunto);
        raiz.put("status", "sent");

        ArrayNode documentos = raiz.putArray("documents");
        ArrayNode signHere = mapper.createArrayNode();
        for (DocumentoEnvio d : docs) {
            ObjectNode doc = documentos.addObject();
            doc.put("documentId", d.documentId());
            doc.put("name", d.filename());
            doc.put("fileExtension", "docx");
            doc.put("documentBase64", Base64.getEncoder().encodeToString(d.conteudo()));
            // Um campo de assinatura por documento, ancorado no marcador invisível (some no free-form se ausente).
            ObjectNode tab = signHere.addObject();
            tab.put("documentId", d.documentId());
            tab.put("anchorString", ANCORA_ASSINATURA);
            tab.put("anchorUnits", "pixels");
            tab.put("anchorXOffset", "0");
            tab.put("anchorYOffset", "0");
            tab.put("anchorIgnoreIfNotPresent", "true");
        }

        ObjectNode signer = mapper.createObjectNode();
        signer.put("recipientId", "1");
        signer.put("name", nome(s));
        signer.put("email", email(s));
        signer.put("clientUserId", clientUserId(s)); // marca como signatário EMBUTIDO (não recebe e-mail)
        signer.set("tabs", mapper.createObjectNode().set("signHereTabs", signHere));
        ObjectNode recipients = mapper.createObjectNode();
        recipients.set("signers", mapper.createArrayNode().add(signer));
        raiz.set("recipients", recipients);

        if (!webhookUrl.isEmpty()) {
            raiz.set("eventNotification", eventNotification());
        }

        JsonNode resp = post("/restapi/v2.1/accounts/" + accountId + "/envelopes", raiz);
        String envelopeId = resp.path("envelopeId").asText(null);
        if (envelopeId == null || envelopeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSign não devolveu o envelopeId.");
        }
        return envelopeId;
    }

    /** Abre a cerimônia embutida (recipient view) e devolve a URL para o WebView do app. */
    public String urlCerimonia(String envelopeId, AssinaturaProvider.Signatario s) {
        exigirConfig();
        ObjectNode body = mapper.createObjectNode();
        body.put("returnUrl", retornoPageBase + "/assinatura/docusign/retorno");
        body.put("authenticationMethod", "none");
        body.put("email", email(s));
        body.put("userName", nome(s));
        body.put("clientUserId", clientUserId(s));
        body.put("recipientId", "1");
        JsonNode resp = post("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId
                + "/views/recipient", body);
        String url = resp.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSign não devolveu a URL da cerimônia.");
        }
        return url;
    }

    /**
     * Adiciona um signatário ao envelope JÁ enviado (coassinatura). O signatário é EMBUTIDO (clientUserId) e
     * fica com um campo de assinatura ancorado em {@code anchor} em CADA documento de conteúdo do envelope.
     * O {@code routingOrder} maior faz o DocuSign só liberar este signatário depois do anterior (ordem nativa).
     */
    public void adicionarSignatario(String envelopeId, String recipientId, String routingOrder,
            AssinaturaProvider.Signatario s, String anchor) {
        exigirConfig();
        ArrayNode signHere = mapper.createArrayNode();
        for (String documentId : documentosDoEnvelope(envelopeId)) {
            ObjectNode tab = signHere.addObject();
            tab.put("documentId", documentId);
            tab.put("anchorString", anchor);
            tab.put("anchorUnits", "pixels");
            tab.put("anchorXOffset", "0");
            tab.put("anchorYOffset", "0");
            tab.put("anchorIgnoreIfNotPresent", "true");
        }
        ObjectNode signer = mapper.createObjectNode();
        signer.put("recipientId", recipientId);
        signer.put("routingOrder", routingOrder);
        signer.put("name", nome(s));
        signer.put("email", email(s));
        signer.put("clientUserId", clientUserId(s));
        signer.set("tabs", mapper.createObjectNode().set("signHereTabs", signHere));
        ObjectNode raiz = mapper.createObjectNode();
        raiz.set("signers", mapper.createArrayNode().add(signer));
        post("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId
                + "/recipients?resend_envelope=false", raiz);
    }

    /**
     * true se o recipient indicado JÁ concluiu ("completed"). É por-recipient de propósito: o status do
     * ENVELOPE fica "sent" enquanto ambos não terminam, então não distingue "paciente concluiu, profissional pendente".
     */
    public boolean recipienteConcluiu(String envelopeId, String recipientId) {
        exigirConfig();
        JsonNode resp = get("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId
                + "/recipients?include_tabs=false");
        for (JsonNode s : resp.path("signers")) {
            if (recipientId != null && recipientId.equals(s.path("recipientId").asText(null))) {
                return "completed".equalsIgnoreCase(s.path("status").asText(""));
            }
        }
        return false;
    }

    /**
     * Abre a recipient view (cerimônia embutida) de um recipient específico — usado para o PROFISSIONAL. Só
     * funciona quando é a vez dele (depois do paciente concluir); senão o DocuSign devolve RECIPIENT_NOT_IN_SEQUENCE.
     * A URL é de USO ÚNICO e expira em minutos → gerar sob demanda. frameAncestors/messageOrigins liberam o iframe.
     */
    public String urlCerimoniaRecipiente(String envelopeId, String recipientId, AssinaturaProvider.Signatario s) {
        exigirConfig();
        ObjectNode body = mapper.createObjectNode();
        body.put("returnUrl", retornoPageBase + "/assinatura/docusign/retorno");
        body.put("authenticationMethod", "none");
        body.put("email", email(s));
        body.put("userName", nome(s));
        body.put("clientUserId", clientUserId(s));
        body.put("recipientId", recipientId);
        if (!frameAncestors.isEmpty()) {
            ArrayNode fa = body.putArray("frameAncestors");
            frameAncestors.forEach(fa::add);
            ArrayNode mo = body.putArray("messageOrigins");
            messageOrigins.forEach(mo::add);
        }
        JsonNode resp = post("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId
                + "/views/recipient", body);
        String url = resp.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "DocuSign não devolveu a URL da cerimônia do profissional.");
        }
        return url;
    }

    /** Status do envelope ("sent", "completed", "declined", "voided"...). */
    public String statusEnvelope(String envelopeId) {
        exigirConfig();
        return get("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId).path("status").asText("");
    }

    /** Ids dos documentos de CONTEÚDO do envelope (exclui certificado/resumo). */
    public List<String> documentosDoEnvelope(String envelopeId) {
        exigirConfig();
        JsonNode resp = get("/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId + "/documents");
        List<String> ids = new ArrayList<>();
        for (JsonNode d : resp.path("envelopeDocuments")) {
            String id = d.path("documentId").asText("");
            String tipo = d.path("type").asText("");
            if (id.isBlank() || "certificate".equalsIgnoreCase(id) || "summary".equalsIgnoreCase(tipo)) {
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    /** Baixa o PDF de um documento do envelope. */
    public byte[] baixarDocumento(String envelopeId, String documentId) {
        exigirConfig();
        return client().get()
                .uri(accountBaseUri + "/restapi/v2.1/accounts/" + accountId + "/envelopes/" + envelopeId
                        + "/documents/" + documentId)
                .header("Authorization", "Bearer " + obterAccessToken())
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "DocuSign respondeu " + res.getStatusCode().value() + " ao baixar o documento.");
                })
                .body(byte[].class);
    }

    // ---- OAuth (JWT Grant) ----

    private synchronized String obterAccessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpira)) {
            return accessToken;
        }
        String assertion = montarAssertion();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);

        String corpo = client().post().uri(oauthBase + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .exchange((req, res) -> {
                    String texto = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (res.getStatusCode().isError()) {
                        if (texto.contains("consent_required")) {
                            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "DocuSign exige consentimento único do usuário antes de assinar. Abra a URL de "
                                            + "consentimento (admin) e conceda o acesso ao aplicativo.");
                        }
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "DocuSign OAuth respondeu " + res.getStatusCode().value() + ": " + resumir(texto));
                    }
                    return texto;
                });

        JsonNode json = ler(corpo);
        String token = json.path("access_token").asText(null);
        long expiraEm = json.path("expires_in").asLong(3600);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DocuSign OAuth não devolveu access_token.");
        }
        this.accessToken = token;
        this.accessTokenExpira = Instant.now().plusSeconds(Math.max(60, expiraEm - 60));
        return token;
    }

    private String montarAssertion() {
        try {
            Instant agora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(integrationKey)
                    .subject(userId)
                    .audience(audienceHost())
                    .issueTime(Date.from(agora))
                    .expirationTime(Date.from(agora.plusSeconds(3600)))
                    .claim("scope", scope)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao assinar o JWT do DocuSign (chave inválida?).");
        }
    }

    /** aud do JWT = host do OAuth sem esquema (ex.: account-d.docusign.com). */
    private String audienceHost() {
        String h = oauthBase;
        int i = h.indexOf("://");
        if (i >= 0) {
            h = h.substring(i + 3);
        }
        int barra = h.indexOf('/');
        return barra >= 0 ? h.substring(0, barra) : h;
    }

    // ---- eventNotification (Connect) ----

    private ObjectNode eventNotification() {
        ObjectNode en = mapper.createObjectNode();
        en.put("url", webhookUrl);
        en.put("loggingEnabled", "true");
        en.put("requireAcknowledgment", "true");
        en.put("includeDocuments", "false"); // baixamos o PDF pela API
        en.set("eventData", mapper.createObjectNode().put("version", "restv2.1"));
        ArrayNode eventos = en.putArray("envelopeEvents");
        for (String status : new String[] { "completed", "declined", "voided" }) {
            eventos.addObject().put("envelopeEventStatusCode", status);
        }
        return en;
    }

    // ---- helpers HTTP ----

    private JsonNode post(String caminho, Object body) {
        return ler(client().post().uri(accountBaseUri + caminho)
                .header("Authorization", "Bearer " + obterAccessToken())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(escrever(body)).retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    String texto = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "DocuSign respondeu " + res.getStatusCode().value() + " em " + caminho + ": "
                                    + resumir(texto));
                }).body(String.class));
    }

    private JsonNode get(String caminho) {
        return ler(client().get().uri(accountBaseUri + caminho)
                .header("Authorization", "Bearer " + obterAccessToken())
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "DocuSign respondeu " + res.getStatusCode().value() + " em " + caminho + ".");
                }).body(String.class));
    }

    private synchronized RestClient client() {
        if (restClient == null) {
            // HTTP/1.1 forçado: o JDK HttpClient (HTTP/2) TRAVA no POST com corpo grande (envelope com vários
            // .docx) para demo.docusign.net — o mesmo request completa em ~1s por HTTP/1.1. Ver diagnóstico.
            HttpClient http = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(connectTimeout))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
            factory.setReadTimeout(Duration.ofSeconds(readTimeout));
            restClient = RestClient.builder().requestFactory(factory).build();
        }
        return restClient;
    }

    private void exigirConfig() {
        if (!temCredenciais()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura eletrônica indisponível (credenciais do DocuSign não configuradas).");
        }
    }

    // ---- signatário ----

    private static String nome(AssinaturaProvider.Signatario s) {
        String n = s == null ? null : s.nome();
        return n == null || n.isBlank() ? "Paciente" : n;
    }

    /** E-mail placeholder (o signatário é EMBUTIDO por clientUserId → o DocuSign não envia e-mail). */
    private static String email(AssinaturaProvider.Signatario s) {
        String ext = s == null ? null : s.externalId();
        String base = ext == null || ext.isBlank() ? "paciente" : ext.replaceAll("[^a-zA-Z0-9_-]", "");
        if (base.isBlank()) {
            base = "paciente";
        }
        return base + "@no-reply.integrasaude.app";
    }

    private static String clientUserId(AssinaturaProvider.Signatario s) {
        String ext = s == null ? null : s.externalId();
        return ext == null || ext.isBlank() ? "paciente" : ext;
    }

    // ---- chave / util ----

    private static PrivateKey carregarChave(String base64, String path) {
        byte[] der = null;
        try {
            if (base64 != null && !base64.isBlank()) {
                der = Base64.getDecoder().decode(base64.trim().replaceAll("\\s", ""));
            } else if (path != null && !path.isBlank()) {
                java.nio.file.Path p = java.nio.file.Path.of(path.trim());
                if (java.nio.file.Files.exists(p)) {
                    String pem = java.nio.file.Files.readString(p);
                    String limpo = pem.replaceAll("-----BEGIN [^-]+-----", "")
                            .replaceAll("-----END [^-]+-----", "")
                            .replaceAll("\\s", "");
                    if (!limpo.isBlank()) {
                        der = Base64.getDecoder().decode(limpo);
                    }
                }
            }
            if (der == null) {
                return null; // sem chave → provedor indisponível (503 nas operações)
            }
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            // Chave malformada: trata como ausente (o provedor fica indisponível) para não derrubar o boot.
            return null;
        }
    }

    private static String semBarra(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    /** Divide uma lista separada por vírgula em origens limpas (sem barra final, sem vazios). */
    private static List<String> listaCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String p : csv.split(",")) {
                String v = semBarra(p);
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String escrever(Object o) {
        try {
            return o instanceof JsonNode ? mapper.writeValueAsString(o) : mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao montar requisição DocuSign.");
        }
    }

    private JsonNode ler(String resp) {
        try {
            return mapper.readTree(resp == null || resp.isBlank() ? "{}" : resp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida do DocuSign.");
        }
    }

    private static String resumir(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }

    /** Helpers de config para outras classes (ex.: montar a URL de consentimento no controller dev). */
    public String integrationKey() {
        return integrationKey;
    }

    public String oauthBase() {
        return oauthBase;
    }

    public String scope() {
        return scope;
    }
}
