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

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
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

    private static final String BASE_SANDBOX = "https://sandbox.api.zapsign.com.br/api/v1";
    private static final String BASE_PRODUCAO = "https://api.zapsign.com.br/api/v1";

    private final ConfiguracaoService configuracaoService;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    public ZapSignClient(
            ConfiguracaoService configuracaoService,
            @Value("${zapsign.connect-timeout-segundos:5}") int connectTimeout,
            @Value("${zapsign.read-timeout-segundos:20}") int readTimeout) {
        this.configuracaoService = configuracaoService;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /** Token da API do ZapSign (SEGREDO) lido do banco, cifrado, em runtime. */
    private String apiToken() {
        return configuracaoService.lerSegredo(ChaveConfiguracao.ZAPSIGN_API_TOKEN);
    }

    /** Base URL: a URL (editável) do ambiente ativo; cai nas constantes se a config estiver vazia. */
    private String baseUrl() {
        String amb = configuracaoService.lerTexto(ChaveConfiguracao.ZAPSIGN_AMBIENTE);
        boolean prod = "PRODUCAO".equalsIgnoreCase(amb == null ? "" : amb.trim());
        String url = configuracaoService.lerTexto(prod ? ChaveConfiguracao.ZAPSIGN_URL_PRODUCAO : ChaveConfiguracao.ZAPSIGN_URL_SANDBOX);
        if (url == null || url.isBlank()) {
            return prod ? BASE_PRODUCAO : BASE_SANDBOX;
        }
        // remove barra final para casar com os paths ("/templates/..."), que já começam com "/"
        return url.trim().replaceAll("/+$", "");
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
                    "Assinatura eletrônica indisponível (token ZapSign não configurado).");
        }
    }

    /** true se o token da API está configurado (usado por {@code ZapSignProvider.disponivel()}). */
    public boolean temToken() {
        return configuracaoService.segredoPreenchido(ChaveConfiguracao.ZAPSIGN_API_TOKEN);
    }

    /** Teste de conexão: chamada autenticada leve (1ª página de modelos). Lança em erro de token/rede. */
    public void testar() {
        exigirToken();
        getRawBody("/templates/?page=1");
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

    /** Resumo de um Modelo (template) para o seletor no back-office. */
    public record ModeloResumo(String token, String nome, String tipo, boolean ativo) {
    }

    /** Uma variável ({{...}}) que o Modelo do ZapSign espera. */
    public record ModeloVariavel(String variable, String label, boolean required) {
    }

    /** Detalhe de um Modelo: nome + variáveis (inputs) que ele espera. */
    public record ModeloDetalhe(String token, String nome, List<ModeloVariavel> variaveis) {
    }

    /**
     * Lista os Modelos (templates) da conta ZapSign — para o admin escolher no cadastro do termo. Agrega as
     * páginas (20/página) até acabar, com teto de 10 páginas (200 modelos) para não varrer contas enormes.
     */
    public List<ModeloResumo> listarModelos() {
        exigirToken();
        List<ModeloResumo> out = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            JsonNode resp = getJson("/templates/?page=" + page);
            JsonNode results = resp.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            for (JsonNode t : results) {
                out.add(new ModeloResumo(texto(t, "token"), texto(t, "name"),
                        texto(t, "template_type"), t.path("active").asBoolean(true)));
            }
            JsonNode next = resp.path("next");
            if (next.isNull() || next.asText("").isBlank()) {
                break;
            }
        }
        return out;
    }

    /** Detalha um Modelo (nome + variáveis {{...}} esperadas) — usado para mapear/avisar no cadastro. */
    public ModeloDetalhe detalharModelo(String templateToken) {
        exigirToken();
        JsonNode t = getJson("/templates/" + templateToken + "/");
        List<ModeloVariavel> variaveis = new ArrayList<>();
        JsonNode inputs = t.path("inputs");
        if (inputs.isArray()) {
            for (JsonNode in : inputs) {
                variaveis.add(new ModeloVariavel(texto(in, "variable"), texto(in, "label"),
                        in.path("required").asBoolean(false)));
            }
        }
        return new ModeloDetalhe(texto(t, "token"), texto(t, "name"), variaveis);
    }

    /** Signatário adicionado a um documento existente (coassinatura): token + link da cerimônia. */
    public record SignatarioAdicionado(String signerToken, String signUrl) {
    }

    /**
     * Adiciona um signatário a um documento JÁ criado ({@code POST /docs/{token}/add-signer/}) — usado na
     * COASSINATURA do profissional. Assinatura em tela, sem e-mail/WhatsApp automático. Devolve token + sign_url.
     */
    public SignatarioAdicionado adicionarSignatario(String docToken, Signatario s) {
        exigirToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", s.nome());
        if (s.cpf() != null && !s.cpf().isBlank()) {
            body.put("cpf", s.cpf());
        }
        if (s.phoneNumber() != null && !s.phoneNumber().isBlank()) {
            body.put("phone_country", s.phoneCountry() == null ? "55" : s.phoneCountry());
            body.put("phone_number", s.phoneNumber());
        }
        body.put("auth_mode", s.authMode() == null ? "assinaturaTela" : s.authMode());
        body.put("send_automatic_email", false);
        body.put("send_automatic_whatsapp", false);
        if (s.qualification() != null) {
            body.put("qualification", s.qualification());
        }
        if (s.externalId() != null) {
            body.put("external_id", s.externalId());
        }
        JsonNode resp = postJson("/docs/" + docToken + "/add-signer/", body);
        return new SignatarioAdicionado(texto(resp, "token"), texto(resp, "sign_url"));
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
                .uri(baseUrl() + caminho)
                .header("Authorization", "Bearer " + apiToken())
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
                .uri(baseUrl() + caminho)
                .header("Authorization", "Bearer " + apiToken())
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
