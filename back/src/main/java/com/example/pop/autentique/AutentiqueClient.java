package com.example.pop.autentique;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente HTTP da API Autentique (GraphQL v2). Cria o documento por "GraphQL multipart request" (partes
 * operations/map/file, scalar {@code Upload!}), consulta o documento e baixa o arquivo assinado. O token
 * vem de configuração (env {@code AUTENTIQUE_API_TOKEN}); o modo SANDBOX é por documento (não consome
 * créditos / sem validade jurídica). Falha em 503 quando o token não está configurado.
 */
@Component
public class AutentiqueClient {

    /** A Autentique usa o MESMO endpoint para sandbox e produção (o sandbox é por-documento, não por-URL). */
    private static final String BASE_URL = "https://api.autentique.com.br/v2/graphql";
    private static final String BASE_URL_PRODUCAO = BASE_URL;

    private final ConfiguracaoService configuracaoService;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    public AutentiqueClient(
            ConfiguracaoService configuracaoService,
            @Value("${autentique.connect-timeout-segundos:5}") int connectTimeout,
            @Value("${autentique.read-timeout-segundos:30}") int readTimeout) {
        this.configuracaoService = configuracaoService;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /** Token da API da Autentique (SEGREDO) lido do banco, cifrado, em runtime. */
    private String apiToken() {
        return configuracaoService.lerSegredo(ChaveConfiguracao.AUTENTIQUE_API_TOKEN);
    }

    /** Sandbox por-documento conforme o ambiente (SANDBOX padrão → true; PRODUCAO → false). */
    private boolean sandbox() {
        String amb = configuracaoService.lerTexto(ChaveConfiguracao.AUTENTIQUE_AMBIENTE);
        return !"PRODUCAO".equalsIgnoreCase(amb == null ? "" : amb.trim());
    }

    /** Endpoint GraphQL: a URL (editável) do ambiente ativo; cai na constante se a config estiver vazia. */
    private String baseUrl() {
        boolean prod = !sandbox();
        String url = configuracaoService.lerTexto(prod ? ChaveConfiguracao.AUTENTIQUE_URL_PRODUCAO : ChaveConfiguracao.AUTENTIQUE_URL_SANDBOX);
        if (url == null || url.isBlank()) {
            return prod ? BASE_URL_PRODUCAO : BASE_URL;
        }
        return url.trim();
    }

    /** Resultado da criação: o essencial para a cerimônia embutida e o acompanhamento. */
    public record DocumentoCriado(String documentId, String signerId, String shortLink) {
    }

    public boolean temToken() {
        return configuracaoService.segredoPreenchido(ChaveConfiguracao.AUTENTIQUE_API_TOKEN);
    }

    /** Teste de conexão: consulta autenticada leve (1 documento). Lança/erra em token inválido. */
    public void testar() {
        exigirToken();
        dadosOuErro(postJson("query { documents(limit: 1, page: 1) { total } }", Map.of()));
    }

    /**
     * Cria um documento a partir do arquivo (bytes) para UM signatário sem e-mail (assinatura em tela),
     * de onde vem o {@code short_link} da cerimônia. {@code sandbox} conforme a configuração.
     */
    public DocumentoCriado criarDocumento(String nome, byte[] arquivo, String contentType, String filename,
            AssinaturaProvider.Signatario s) {
        exigirToken();

        String query = "mutation($document: DocumentInput!, $signers: [SignerInput!]!, $file: Upload!) {"
                + " createDocument(sandbox: " + sandbox() + ", document: $document, signers: $signers, file: $file) {"
                + " id signatures { public_id link { short_link } } } }";

        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("name", s.nome());
        signer.put("action", "SIGN"); // assinatura eletrônica em tela, sem certificado

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("document", Map.of("name", nome == null ? "Documento" : nome));
        variables.put("signers", List.of(signer));
        variables.put("file", null);

        Map<String, Object> operations = new LinkedHashMap<>();
        operations.put("query", query);
        operations.put("variables", variables);

        String operationsJson = escrever(operations);
        String mapJson = "{\"0\":[\"variables.file\"]}";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("operations", operationsJson, MediaType.APPLICATION_JSON);
        builder.part("map", mapJson, MediaType.APPLICATION_JSON);
        builder.part("0", new ByteArrayResource(arquivo) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, HttpEntity<?>> body = builder.build();

        String resp = client().post()
                .uri(baseUrl())
                .header("Authorization", "Bearer " + apiToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Autentique respondeu " + res.getStatusCode().value() + " ao criar o documento.");
                })
                .body(String.class);

        JsonNode dados = dadosOuErro(resp);
        JsonNode doc = dados.path("createDocument");
        // A Autentique inclui o DONO da conta em signatures[] (action null, sem link) além do nosso
        // signatário: pegamos a assinatura que TEM short_link (o paciente que vai assinar em tela).
        JsonNode assinaturas = doc.path("signatures");
        String signerId = null;
        String shortLink = null;
        if (assinaturas.isArray()) {
            for (JsonNode sig : assinaturas) {
                String sl = texto(sig.path("link"), "short_link");
                if (sl != null && !sl.isBlank()) {
                    signerId = texto(sig, "public_id");
                    shortLink = sl;
                    break;
                }
            }
            if (signerId == null && assinaturas.size() > 0) {
                signerId = texto(assinaturas.path(0), "public_id"); // fallback improvável
            }
        }
        return new DocumentoCriado(texto(doc, "id"), signerId, shortLink);
    }

    /**
     * URL do PDF assinado — SÓ quando todos os signatários com ação SIGN já assinaram (a Autentique devolve
     * a URL de {@code assinado.pdf} mesmo antes de assinar, então conferimos as assinaturas para não baixar
     * um PDF ainda não assinado). Null enquanto pendente.
     */
    public String urlAssinado(String documentId) {
        exigirToken();
        String query = "query($id: UUID!) { document(id: $id) {"
                + " files { signed } signatures { action { name } signed { created_at } } } }";
        JsonNode doc = dadosOuErro(postJson(query, Map.of("id", documentId))).path("document");
        if (!assinaturasConcluidas(doc.path("signatures"))) {
            return null; // ainda falta algum signatário SIGN
        }
        JsonNode signed = doc.path("files").path("signed");
        return signed.isMissingNode() || signed.isNull() || signed.asText().isBlank() ? null : signed.asText();
    }

    /**
     * Adiciona um signatário (o PROFISSIONAL, coassinatura) a um documento JÁ criado, SEM e-mail (name-only) —
     * assim a Autentique devolve o {@code short_link} da cerimônia (com e-mail, ela envia e o link vem null).
     * {@code action=SIGN} = assinatura eletrônica em tela (igual ao paciente). Devolve public_id + short_link.
     */
    public DocumentoCriado adicionarSignatario(String documentId, String nome, String email) {
        exigirToken();
        String query = "mutation($document_id: UUID!, $signer: SignerInput!) {"
                + " createSigner(document_id: $document_id, signer: $signer) {"
                + " public_id link { short_link } } }";
        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("name", nome == null || nome.isBlank() ? "Profissional de saúde" : nome);
        signer.put("action", "SIGN");
        if (email != null && !email.isBlank()) {
            // delivery_method LINK: a Autentique GUARDA o e-mail (pré-preenche a cerimônia) mas NÃO envia e-mail
            // e MANTÉM o short_link. Passar e-mail SEM isso faria a Autentique enviar e-mail e devolver link null.
            signer.put("email", email.trim());
            signer.put("delivery_method", "DELIVERY_METHOD_LINK");
        }
        JsonNode data = dadosOuErro(postJson(query, Map.of("document_id", documentId, "signer", signer)));
        JsonNode cs = data.path("createSigner");
        return new DocumentoCriado(documentId, texto(cs, "public_id"), texto(cs.path("link"), "short_link"));
    }

    /** DEV: JSON cru do documento (todos os signatários, link e status) — para inspeção da coassinatura. */
    public String detalharRaw(String documentId) {
        exigirToken();
        String query = "query($id: UUID!) { document(id: $id) { id name"
                + " signatures { public_id name email created_at action { name }"
                + " signed { created_at } viewed { created_at } rejected { created_at } link { short_link } } } }";
        return postJson(query, Map.of("id", documentId));
    }

    /** true se o signatário indicado (public_id) JÁ assinou (signed.created_at presente) — checagem por-signatário. */
    public boolean signatarioAssinou(String documentId, String publicId) {
        exigirToken();
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        String query = "query($id: UUID!) { document(id: $id) {"
                + " signatures { public_id signed { created_at } } } }";
        JsonNode doc = dadosOuErro(postJson(query, Map.of("id", documentId))).path("document");
        for (JsonNode s : doc.path("signatures")) {
            if (publicId.equals(texto(s, "public_id"))) {
                JsonNode quando = s.path("signed").path("created_at");
                return !(quando.isMissingNode() || quando.isNull() || quando.asText().isBlank());
            }
        }
        return false;
    }

    /** true se há ao menos um signatário SIGN e TODOS os SIGN já assinaram. */
    private static boolean assinaturasConcluidas(JsonNode signatures) {
        if (!signatures.isArray() || signatures.isEmpty()) {
            return false;
        }
        boolean temSign = false;
        for (JsonNode s : signatures) {
            if ("SIGN".equals(s.path("action").path("name").asText(null))) {
                temSign = true;
                JsonNode quando = s.path("signed").path("created_at");
                if (quando.isMissingNode() || quando.isNull() || quando.asText().isBlank()) {
                    return false;
                }
            }
        }
        return temSign;
    }

    /**
     * Baixa o binário de uma URL (arquivo assinado). Para URLs da API da Autentique ({@code assinado.pdf})
     * envia o Bearer (elas exigem auth); para URLs de storage pré-assinadas (GCS) vai sem header.
     */
    public byte[] baixar(String url) {
        RestClient.RequestHeadersSpec<?> req = client().get().uri(java.net.URI.create(url));
        if (url != null && url.contains("api.autentique.com.br")) {
            req = req.header("Authorization", "Bearer " + apiToken());
        }
        return req.retrieve().body(byte[].class);
    }

    // ---- helpers ----

    private String postJson(String query, Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("variables", variables);
        return client().post()
                .uri(baseUrl())
                .header("Authorization", "Bearer " + apiToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(escrever(payload))
                .retrieve()
                .onStatus(st -> st.is4xxClientError() || st.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Autentique respondeu " + res.getStatusCode().value() + " ao consultar o documento.");
                })
                .body(String.class);
    }

    /** Lê a resposta GraphQL: erro se vier {@code errors[]}; senão devolve o nó {@code data}. */
    private JsonNode dadosOuErro(String resp) {
        JsonNode raiz = ler(resp);
        JsonNode errors = raiz.path("errors");
        if (errors.isArray() && errors.size() > 0) {
            String msg = errors.path(0).path("message").asText("erro desconhecido");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Autentique: " + msg);
        }
        return raiz.path("data");
    }

    private synchronized RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
            factory.setReadTimeout(Duration.ofSeconds(readTimeout));
            restClient = RestClient.builder().requestFactory(factory).build();
        }
        return restClient;
    }

    private void exigirToken() {
        if (!temToken()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura eletrônica indisponível (token Autentique não configurado).");
        }
    }

    private String escrever(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao montar requisição Autentique.");
        }
    }

    private JsonNode ler(String resp) {
        try {
            return mapper.readTree(resp == null ? "{}" : resp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida da Autentique.");
        }
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
