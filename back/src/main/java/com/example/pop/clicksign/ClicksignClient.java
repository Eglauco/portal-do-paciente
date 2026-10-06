package com.example.pop.clicksign;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente HTTP da API Clicksign v3 (envelopes, padrão JSON:API). Encadeia a cerimônia: criar envelope →
 * adicionar documento(s) (arquivo pronto em content_base64) → criar signatário → requisitos (assinar em
 * tela sem certificado) → ativar (running). Também detalha o documento e baixa o assinado. Auth por access
 * token no header {@code Authorization} (SEM "Bearer"). Falha em 503 quando o token não está configurado.
 */
@Component
public class ClicksignClient {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    private static final String BASE_SANDBOX = "https://sandbox.clicksign.com/api/v3";
    private static final String BASE_PRODUCAO = "https://app.clicksign.com/api/v3";

    private final ConfiguracaoService configuracaoService;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    public ClicksignClient(
            ConfiguracaoService configuracaoService,
            @Value("${clicksign.connect-timeout-segundos:5}") int connectTimeout,
            @Value("${clicksign.read-timeout-segundos:30}") int readTimeout) {
        this.configuracaoService = configuracaoService;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /** Access token da Clicksign (SEGREDO) lido do banco, cifrado, em runtime. */
    private String accessToken() {
        return configuracaoService.lerSegredo(ChaveConfiguracao.CLICKSIGN_ACCESS_TOKEN);
    }

    /** Base URL: a URL (editável) do ambiente ativo; cai nas constantes se a config estiver vazia. */
    String baseUrl() {
        String amb = configuracaoService.lerTexto(ChaveConfiguracao.CLICKSIGN_AMBIENTE);
        boolean prod = "PRODUCAO".equalsIgnoreCase(amb == null ? "" : amb.trim());
        String url = configuracaoService.lerTexto(prod ? ChaveConfiguracao.CLICKSIGN_URL_PRODUCAO : ChaveConfiguracao.CLICKSIGN_URL_SANDBOX);
        if (url == null || url.isBlank()) {
            return prod ? BASE_PRODUCAO : BASE_SANDBOX;
        }
        return url.trim().replaceAll("/+$", "");
    }

    /**
     * Host do widget embedded (origin da URL base, sem o sufixo {@code /api/vN}). Ex.:
     * {@code https://sandbox.clicksign.com/api/v3} → {@code https://sandbox.clicksign.com}.
     */
    public String hostWidget() {
        String base = baseUrl();
        int i = base.indexOf("/api/");
        return i > 0 ? base.substring(0, i) : base;
    }

    public boolean temToken() {
        return configuracaoService.segredoPreenchido(ChaveConfiguracao.CLICKSIGN_ACCESS_TOKEN);
    }

    /** Teste de conexão: lista envelopes (chamada autenticada leve). Lança em token inválido/rede. */
    public void testar() {
        exigirToken();
        get("/envelopes");
    }

    /** Cria um envelope (status draft) e devolve o id. */
    public String criarEnvelope(String nome) {
        exigirToken();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", nome == null || nome.isBlank() ? "Termos de Consentimento" : nome);
        attrs.put("locale", "pt-BR");
        attrs.put("auto_close", true);
        JsonNode resp = post("/envelopes", dado("envelopes", attrs, null));
        return id(resp);
    }

    /** Adiciona um documento (arquivo pronto) ao envelope e devolve o id do documento. */
    public String adicionarDocumento(String envelopeId, String filename, byte[] conteudo, String mime) {
        exigirToken();
        String dataUri = "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(conteudo);
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("filename", filename);
        attrs.put("content_base64", dataUri);
        JsonNode resp = post("/envelopes/" + envelopeId + "/documents", dado("documents", attrs, null));
        return id(resp);
    }

    /** Cria o signatário (sem e-mail; notifica só o documento assinado por WhatsApp). Devolve o id. */
    public String criarSignatario(String envelopeId, AssinaturaProvider.Signatario s) {
        exigirToken();
        boolean temTelefone = s.phoneNumber() != null && !s.phoneNumber().isBlank();
        boolean temEmail = s.email() != null && !s.email().isBlank();
        Map<String, Object> comunicar = new LinkedHashMap<>();
        // document_signed NÃO aceita "none" (obrigatório email/whatsapp): whatsapp quando há telefone (paciente),
        // senão e-mail (ex.: profissional sem telefone).
        comunicar.put("document_signed", temTelefone ? "whatsapp" : (temEmail ? "email" : "whatsapp"));
        comunicar.put("signature_request", "none");
        comunicar.put("signature_reminder", "none");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", s.nome());
        if (s.cpf() != null && !s.cpf().isBlank()) {
            attrs.put("has_documentation", true);
            attrs.put("documentation", formatarCpf(s.cpf()));
        }
        if (temTelefone) {
            attrs.put("phone_number", s.phoneNumber());
        }
        if (temEmail) {
            attrs.put("email", s.email());
        }
        attrs.put("communicate_events", comunicar);
        JsonNode resp = post("/envelopes/" + envelopeId + "/signers", dado("signers", attrs, null));
        return id(resp);
    }

    /** Requisito de qualificação: este signatário ASSINA este documento. */
    public void requisitoAssinar(String envelopeId, String documentId, String signerId) {
        criarRequisito(envelopeId, documentId, signerId, attr("action", "agree", "role", "sign"));
    }

    /** Requisito de autenticação por TOKEN (Widget Embedded): sms (padrão) / email / whatsapp, conforme a config. */
    public void requisitoEvidencia(String envelopeId, String documentId, String signerId) {
        criarRequisito(envelopeId, documentId, signerId, attr("action", "provide_evidence", "auth", autenticacao()));
    }

    /** Método de autenticação do signatário (config CLICKSIGN_AUTENTICACAO; sms por padrão). */
    private String autenticacao() {
        String v = configuracaoService.lerTexto(ChaveConfiguracao.CLICKSIGN_AUTENTICACAO);
        return v == null || v.isBlank() ? "sms" : v.trim().toLowerCase();
    }

    /**
     * Chaves dos signatários que JÁ assinaram, lidas do log de eventos do envelope (evento {@code sign}).
     * Usado para a checagem por-signatário na coassinatura (com auth por token a birthday NÃO é preenchida).
     */
    public java.util.Set<String> signatariosQueAssinaram(String envelopeId) {
        exigirToken();
        JsonNode data = get("/envelopes/" + envelopeId + "/events").path("data");
        java.util.Set<String> assinaram = new java.util.HashSet<>();
        if (data.isArray()) {
            for (JsonNode ev : data) {
                JsonNode attrs = ev.path("attributes");
                String nome = attrs.path("name").asText(attrs.path("type").asText(""));
                if ("sign".equalsIgnoreCase(nome)) {
                    JsonNode signer = attrs.path("data").path("signer");
                    String key = signer.path("key").asText(signer.path("id").asText(null));
                    if (key != null && !key.isBlank()) {
                        assinaram.add(key);
                    }
                }
            }
        }
        return assinaram;
    }

    private void criarRequisito(String envelopeId, String documentId, String signerId, Map<String, Object> attrs) {
        exigirToken();
        Map<String, Object> rels = new LinkedHashMap<>();
        rels.put("document", Map.of("data", Map.of("type", "documents", "id", documentId)));
        rels.put("signer", Map.of("data", Map.of("type", "signers", "id", signerId)));
        post("/envelopes/" + envelopeId + "/requirements", dado("requirements", attrs, rels));
    }

    /** Ativa o envelope (draft → running): a partir daí a assinatura vale. */
    public void ativar(String envelopeId) {
        exigirToken();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", envelopeId);
        data.put("type", "envelopes");
        data.put("attributes", Map.of("status", "running"));
        patch("/envelopes/" + envelopeId, Map.of("data", data));
    }

    /** Detalha o documento (só {@code attributes}: status, filename…). Precisa do envelope (v3 aninha o documento). */
    public JsonNode detalharDocumento(String envelopeId, String documentId) {
        exigirToken();
        return get("/envelopes/" + envelopeId + "/documents/" + documentId).path("data").path("attributes");
    }

    /**
     * Documento completo (node {@code data}: {@code attributes} + {@code links}). A URL do PDF assinado no v3
     * fica em {@code data.links.files.signed} (S3 pré-assinada, expira ~5min), NÃO em attributes.downloads.
     */
    public JsonNode detalharDocumentoCompleto(String envelopeId, String documentId) {
        exigirToken();
        return get("/envelopes/" + envelopeId + "/documents/" + documentId).path("data");
    }

    /** Detalha o envelope (status + downloads do envelope, ex.: arquivo combinado). */
    public JsonNode detalharEnvelope(String envelopeId) {
        exigirToken();
        return get("/envelopes/" + envelopeId).path("data").path("attributes");
    }

    /** Baixa o binário de uma URL (arquivo assinado). URL pré-assinada (S3), sem header de auth. */
    public byte[] baixar(String url) {
        return client().get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
    }

    /** DEV: GET cru (JSON completo) de um caminho relativo — usado pelo inspetor para depurar o 500 do widget. */
    public String getRaw(String caminho) {
        exigirToken();
        return get(caminho).toString();
    }

    // ---- helpers HTTP (JSON:API) ----

    private JsonNode post(String caminho, Object body) {
        return ler(client().post().uri(baseUrl() + caminho)
                .header("Authorization", accessToken()).accept(JSON_API).contentType(JSON_API)
                .body(escrever(body)).retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Clicksign respondeu " + res.getStatusCode().value() + " em " + caminho + ".");
                }).body(String.class));
    }

    private JsonNode patch(String caminho, Object body) {
        return ler(client().patch().uri(baseUrl() + caminho)
                .header("Authorization", accessToken()).accept(JSON_API).contentType(JSON_API)
                .body(escrever(body)).retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Clicksign respondeu " + res.getStatusCode().value() + " em " + caminho + ".");
                }).body(String.class));
    }

    private JsonNode get(String caminho) {
        return ler(client().get().uri(baseUrl() + caminho)
                .header("Authorization", accessToken()).accept(JSON_API).retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Clicksign respondeu " + res.getStatusCode().value() + " em " + caminho + ".");
                }).body(String.class));
    }

    private static Map<String, Object> dado(String type, Map<String, Object> attrs, Map<String, Object> rels) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("attributes", attrs);
        if (rels != null) {
            data.put("relationships", rels);
        }
        return Map.of("data", data);
    }

    private static Map<String, Object> attr(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String id(JsonNode resp) {
        return resp.path("data").path("id").asText(null);
    }

    /** Formata o CPF (dígitos) como 000.000.000-00 (a Clicksign valida o formato/dígito). */
    private static String formatarCpf(String cpf) {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (d.length() != 11) {
            return cpf;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private synchronized RestClient client() {
        if (restClient == null) {
            // JdkClientHttpRequestFactory (java.net.http.HttpClient): suporta PATCH, exigido pela Clicksign v3
            // para ativar o envelope. O SimpleClientHttpRequestFactory (HttpURLConnection) NÃO suporta PATCH.
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeout))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
            factory.setReadTimeout(Duration.ofSeconds(readTimeout));
            restClient = RestClient.builder().requestFactory(factory).build();
        }
        return restClient;
    }

    private void exigirToken() {
        if (!temToken()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura eletrônica indisponível (token Clicksign não configurado).");
        }
    }

    private String escrever(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao montar requisição Clicksign.");
        }
    }

    private JsonNode ler(String resp) {
        try {
            return mapper.readTree(resp == null || resp.isBlank() ? "{}" : resp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida da Clicksign.");
        }
    }
}
