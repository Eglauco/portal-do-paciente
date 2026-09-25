package com.example.pop.zapsign;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente HTTP da API ZapSign (assinatura eletrônica do TCLE). POC: cobre o mínimo do fluxo —
 * criar documento a partir do PDF do termo, detalhar e baixar o arquivo assinado. O token vem
 * de configuração (env ZAPSIGN_API_TOKEN); a base aponta para o SANDBOX por padrão.
 *
 * <p>Segue o padrão de HTTP de saída do projeto (Spring {@link RestClient} + timeouts, como o
 * PushService). Falha de forma segura (503) quando o token não está configurado.
 */
@Service
public class ZapSignClient {

    private final String baseUrl;
    private final String apiToken;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    public ZapSignClient(
            @Value("${zapsign.base-url}") String baseUrl,
            @Value("${zapsign.api-token}") String apiToken,
            @Value("${zapsign.connect-timeout-segundos:5}") int connectTimeout,
            @Value("${zapsign.read-timeout-segundos:20}") int readTimeout) {
        // Normaliza a base sem barra final (montamos os caminhos com "/docs/" etc.).
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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
        if (apiToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura eletrônica indisponível (token ZapSign não configurado).");
        }
    }

    /** Dados de um signatário para a criação do documento. */
    public record Signatario(String nome, String cpf, String phoneCountry, String phoneNumber,
            String authMode, String externalId, String qualification, boolean requireSelfie) {
    }

    /** Resultado da criação: o essencial para conduzir a cerimônia e acompanhar depois. */
    public record DocumentoCriado(String docToken, String signerToken, String signUrl, String status) {
    }

    /**
     * Cria o documento na ZapSign a partir do PDF (base64 SEM o prefixo data:) e um signatário.
     * Não dispara e-mail/WhatsApp (a cerimônia é embutida no app). Retorna o token do documento,
     * o token e o sign_url do signatário.
     */
    public DocumentoCriado criarDocumento(String nome, String base64Pdf, String externalId, Signatario signatario) {
        exigirToken();

        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("name", signatario.nome());
        if (signatario.cpf() != null && !signatario.cpf().isBlank()) {
            signer.put("cpf", signatario.cpf());
        }
        if (signatario.phoneNumber() != null && !signatario.phoneNumber().isBlank()) {
            signer.put("phone_country", signatario.phoneCountry() == null ? "55" : signatario.phoneCountry());
            signer.put("phone_number", signatario.phoneNumber());
        }
        signer.put("auth_mode", signatario.authMode() == null ? "assinaturaTela" : signatario.authMode());
        signer.put("send_automatic_email", false);
        signer.put("send_automatic_whatsapp", false);
        if (signatario.requireSelfie()) {
            signer.put("require_selfie_photo", true);
        }
        if (signatario.qualification() != null) {
            signer.put("qualification", signatario.qualification());
        }
        if (signatario.externalId() != null) {
            signer.put("external_id", signatario.externalId());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", nome);
        body.put("base64_pdf", base64Pdf);
        body.put("lang", "pt-br");
        body.put("disable_signer_emails", true);
        if (externalId != null) {
            body.put("external_id", externalId);
        }
        body.put("signers", List.of(signer));

        JsonNode resp = postJson("/docs/", body);
        JsonNode s0 = resp.path("signers").path(0);
        return new DocumentoCriado(
                texto(resp, "token"),
                texto(s0, "token"),
                texto(s0, "sign_url"),
                texto(resp, "status"));
    }

    /** Um par de substituição de variável do Modelo: {@code de} = token ({{...}}), {@code para} = valor. */
    public record CampoModelo(String de, String para) {
    }

    /**
     * Registra um DOCX como MODELO (template) na ZapSign — é o que permite substituir as variáveis
     * {@code {{...}}}. Recebe o arquivo em base64 (sem prefixo). Retorna o token do modelo.
     */
    public String criarTemplate(String nome, String base64Docx) {
        exigirToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", nome);
        body.put("base64_docx", base64Docx);
        body.put("lang", "pt-br");
        JsonNode resp = postJson("/templates/create/", body);
        return texto(resp, "token");
    }

    /**
     * Cria um documento a partir de um MODELO, preenchendo as variáveis ({@code data} de→para) e
     * definindo o signatário (assinatura em tela, sem e-mail automático). Retorna doc + sign_url.
     */
    public DocumentoCriado criarDocViaModelo(String templateId, Signatario signatario,
            List<CampoModelo> dados, String externalId) {
        exigirToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId);
        body.put("signer_name", signatario.nome());
        if (signatario.phoneNumber() != null && !signatario.phoneNumber().isBlank()) {
            body.put("signer_phone_country", signatario.phoneCountry() == null ? "55" : signatario.phoneCountry());
            body.put("signer_phone_number", signatario.phoneNumber());
        }
        body.put("disable_signer_emails", true);
        if (externalId != null) {
            body.put("external_id", externalId);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (CampoModelo campo : dados) {
            Map<String, Object> par = new LinkedHashMap<>();
            par.put("de", campo.de());
            par.put("para", campo.para() == null ? "" : campo.para());
            data.add(par);
        }
        body.put("data", data);

        JsonNode resp = postJson("/models/create-doc/", body);
        JsonNode s0 = resp.path("signers").path(0);
        return new DocumentoCriado(
                texto(resp, "token"),
                texto(s0, "token"),
                texto(s0, "sign_url"),
                texto(resp, "status"));
    }

    /**
     * Anexa um documento EXTRA (de outro Modelo, com suas variáveis) ao documento principal, para o
     * mesmo signatário assinar TUDO numa cerimônia só (assinatura em lote). Retorna o token do extra.
     * Limite da ZapSign: 14 extras (15 no total). Corpo só aceita template_id + data (sem external_id).
     */
    public String anexarDocExtra(String principalDocToken, String templateId, List<CampoModelo> dados) {
        exigirToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (CampoModelo campo : dados) {
            Map<String, Object> par = new LinkedHashMap<>();
            par.put("de", campo.de());
            par.put("para", campo.para() == null ? "" : campo.para());
            data.add(par);
        }
        body.put("data", data);
        JsonNode resp = postJson("/models/" + principalDocToken + "/upload-extra-doc/", body);
        return texto(resp, "token");
    }

    /** Detalha o documento (status, signers[], original_file, signed_file, signature_report...). */
    public JsonNode detalhar(String docToken) {
        exigirToken();
        return getJson("/docs/" + docToken + "/");
    }

    /** Igual ao {@link #detalhar}, mas devolve o JSON CRU (evita serializar JsonNode como bean no controller). */
    public String detalharRaw(String docToken) {
        exigirToken();
        return getRawBody("/docs/" + docToken + "/");
    }

    /**
     * Baixa o binário de um arquivo da ZapSign (ex.: signed_file / signature_report). São URLs
     * pré-assinadas (S3) que expiram (~60 min) e NÃO usam o header de autorização da API.
     * IMPORTANTE: passa como {@link java.net.URI} (não String) — a assinatura do S3 traz %2F/+ e o
     * RestClient re-encodaria a String (template), quebrando a assinatura → 403.
     */
    public byte[] baixarArquivo(String url) {
        return client().get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
    }

    // ---- helpers HTTP ----

    private JsonNode postJson(String caminho, Object body) {
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao montar requisição ZapSign.");
        }
        String resp = client().post()
                .uri(baseUrl + caminho)
                .header("Authorization", "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "ZapSign respondeu " + res.getStatusCode().value() + " ao criar/consultar documento.");
                })
                .body(String.class);
        return ler(resp);
    }

    private JsonNode getJson(String caminho) {
        return ler(getRawBody(caminho));
    }

    private String getRawBody(String caminho) {
        return client().get()
                .uri(baseUrl + caminho)
                .header("Authorization", "Bearer " + apiToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "ZapSign respondeu " + res.getStatusCode().value() + " ao consultar documento.");
                })
                .body(String.class);
    }

    private JsonNode ler(String resp) {
        try {
            return mapper.readTree(resp == null ? "{}" : resp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida da ZapSign.");
        }
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
