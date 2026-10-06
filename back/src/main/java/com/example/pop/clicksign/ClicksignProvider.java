package com.example.pop.clicksign;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.assinatura.ProvedorAssinatura;
import com.example.pop.assinatura.RenderizadorDocumento;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementação {@link AssinaturaProvider} para a Clicksign (API v3, envelopes). Como não há template por
 * DOCX na API, o POP renderiza o .docx local e envia pronto (igual à Autentique). Uma cerimônia = 1
 * ENVELOPE com 1+ documentos e 1 signatário (todos compartilham o mesmo link). A assinatura em tela é um
 * WIDGET embutido, então o {@code signUrl} aponta para uma página wrapper servida pelo próprio back
 * ({@code /assinatura/clicksign/widget}) que monta o widget. O token guardado é {@code envelopeId/documentId}.
 */
@Component
public class ClicksignProvider implements AssinaturaProvider {

    /** Envia .docx (mesmo padrão de ZapSign/Autentique/DocuSign); o Clicksign converte pra PDF do lado dele. */
    private static final String CONTENT_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ClicksignClient client;
    private final RenderizadorDocumento renderizador;
    private final StorageService storageService;
    private final ConfiguracaoService configuracaoService;
    /** URL base da página-wrapper do widget (infra do próprio back) — não é segredo, segue em config de sistema. */
    private final String widgetPageBase;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClicksignProvider(ClicksignClient client, RenderizadorDocumento renderizador,
            StorageService storageService, ConfiguracaoService configuracaoService,
            @Value("${clicksign.widget-page-base:http://localhost:8080}") String widgetPageBase) {
        this.client = client;
        this.renderizador = renderizador;
        this.storageService = storageService;
        this.configuracaoService = configuracaoService;
        this.widgetPageBase = widgetPageBase != null && widgetPageBase.endsWith("/")
                ? widgetPageBase.substring(0, widgetPageBase.length() - 1) : widgetPageBase;
    }

    /** URL da página-wrapper do widget para um signatário, com o host (origin) derivado da URL base ativa. */
    private String urlWidget(String signerId) {
        return widgetPageBase + "/assinatura/clicksign/widget?signer=" + signerId
                + "&host=" + java.net.URLEncoder.encode(client.hostWidget(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public ProvedorAssinatura id() {
        return ProvedorAssinatura.CLICKSIGN;
    }

    @Override
    public boolean disponivel() {
        return client.temToken();
    }

    @Override
    public ResultadoTeste testarConexao() {
        try {
            client.testar();
            return new ResultadoTeste(true, "Conexão OK — credenciais válidas.");
        } catch (RuntimeException e) {
            return new ResultadoTeste(false, AssinaturaProvider.mensagemErro(e));
        }
    }

    @Override
    public boolean usaModeloRemoto() {
        return false; // renderiza o .docx local e envia pronto (sem template por DOCX na API)
    }

    @Override
    public String registrarModelo(String nome, byte[] docxBytes) {
        return null;
    }

    @Override
    public List<DocumentoAssinatura> criar(List<TermoParaAssinar> termos, Signatario s, boolean combinar) {
        String envelopeId = client.criarEnvelope("Termos de Consentimento");
        String signerId = client.criarSignatario(envelopeId, s);
        String signUrl = urlWidget(signerId);

        List<DocumentoAssinatura> docs = new ArrayList<>();
        // Um único documento combinado cobre todos os termos (uma assinatura só), igual ao modo COMBINADO da
        // Autentique. Enviamos o .docx; o Clicksign converte pra PDF do lado dele (melhor fidelidade que remontar).
        if (combinar || termos.size() > 1) {
            List<byte[]> preenchidos = new ArrayList<>();
            for (TermoParaAssinar t : termos) {
                preenchidos.add(renderizador.preencherDocx(baixarModelo(t), t.variaveis()));
            }
            byte[] docx = renderizador.combinarDocx(preenchidos);
            String docId = adicionarComRequisitos(envelopeId, signerId, docx, "termos.docx");
            docs.add(new DocumentoAssinatura(termos.stream().map(TermoParaAssinar::termoId).toList(),
                    token(envelopeId, docId), signerId, signUrl));
        } else {
            for (TermoParaAssinar t : termos) {
                byte[] docx = renderizador.preencherDocx(baixarModelo(t), t.variaveis());
                String docId = adicionarComRequisitos(envelopeId, signerId, docx, "termo.docx");
                docs.add(new DocumentoAssinatura(List.of(t.termoId()), token(envelopeId, docId), signerId, signUrl));
            }
        }
        // NÃO ativa aqui: a ativação (draft -> running) é adiada p/ finalizarCriacao, DEPOIS da coassinatura —
        // o v3 não deixa adicionar o profissional num envelope já running. Sem coassinatura, finalizarCriacao
        // também é chamado (ativa do mesmo jeito) pelo AssinaturaTermoService ao fim do disparo.
        return docs;
    }

    private String adicionarComRequisitos(String envelopeId, String signerId, byte[] docx, String filename) {
        String docId = client.adicionarDocumento(envelopeId, filename, docx, CONTENT_TYPE_DOCX);
        client.requisitoAssinar(envelopeId, docId, signerId);
        client.requisitoEvidencia(envelopeId, docId, signerId);
        return docId;
    }

    @Override
    public void finalizarCriacao(String providerDocToken) {
        String[] p = separar(providerDocToken);
        if (p != null) {
            client.ativar(p[0]); // draft -> running (só agora a assinatura vale)
        }
    }

    // ---------- Coassinatura do profissional (2º signatário, embedded_signature, ainda em draft) ----------

    @Override
    public boolean suportaCoassinatura() {
        return true;
    }

    @Override
    public boolean cerimoniaCoassinaturaEmbutivel() {
        // Abre em ABA (não iframe), igual a Autentique/DocuSign: nossa página do widget é servida pelo backend
        // com X-Frame-Options padrão (DENY), então não pode ser embutida por outra origin (o front). Na aba, o
        // front detecta a conclusão pelo POLLING (conferirProfissional), não por postMessage.
        return false;
    }

    /**
     * Adiciona o profissional como 2º signatário ao documento principal (envelope AINDA em draft) com os mesmos
     * requisitos do paciente (agree/sign + embedded_signature). A ordem paciente→profissional é garantida pela
     * máquina de estados do POP (o link do profissional só é exposto após o paciente assinar). {@code usarCertificado}
     * é ignorado: o Clicksign aqui é sempre assinatura em tela (cert fica só no ZapSign).
     */
    @Override
    public Coassinante adicionarCoassinante(String providerDocToken, Signatario coSignatario, boolean usarCertificado) {
        String[] p = separar(providerDocToken);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Documento inválido para coassinatura.");
        }
        String envelopeId = p[0];
        String documentId = p[1];
        String signerId = client.criarSignatario(envelopeId, coSignatario);
        client.requisitoAssinar(envelopeId, documentId, signerId);
        client.requisitoEvidencia(envelopeId, documentId, signerId);
        return new Coassinante(signerId, urlWidget(signerId));
    }

    @Override
    public boolean signatarioConcluiu(String providerDocToken, String providerSignerId) {
        String[] p = separar(providerDocToken);
        if (p == null || providerSignerId == null || providerSignerId.isBlank()) {
            return false;
        }
        // Checagem por-signatário pelo log de eventos do envelope (evento "sign" com a chave do signatário).
        return client.signatariosQueAssinaram(p[0]).contains(providerSignerId);
    }

    @Override
    public List<ArquivoAssinado> coletarAssinados(String webhookDocToken) {
        String[] partes = separar(webhookDocToken);
        if (partes == null) {
            return List.of();
        }
        String envelopeId = partes[0];
        String documentId = partes[1];
        JsonNode data = client.detalharDocumentoCompleto(envelopeId, documentId);
        if (!"closed".equals(data.path("attributes").path("status").asText(""))) {
            return List.of(); // ainda não finalizado — não baixa
        }
        // v3: o PDF assinado está em data.links.files.signed (S3 pré-assinada), não em attributes.downloads.
        String url = texto(data.path("links").path("files"), "signed");
        if (url == null) {
            return List.of();
        }
        return List.of(new ArquivoAssinado(webhookDocToken, client.baixar(url)));
    }

    @Override
    public EventoWebhook parseWebhook(Map<String, String> headers, String rawBody) {
        String webhookSecret = configuracaoService.lerSegredo(ChaveConfiguracao.CLICKSIGN_WEBHOOK_SECRET);
        boolean autentico = hmacValido(header(headers, "Content-Hmac"), rawBody, webhookSecret);
        if (!autentico) {
            return new EventoWebhook(false, TipoEvento.OUTRO, List.of());
        }
        JsonNode corpo;
        try {
            corpo = mapper.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (Exception e) {
            return new EventoWebhook(true, TipoEvento.OUTRO, List.of());
        }
        String nome = corpo.path("event").path("name").asText(corpo.path("event").path("type").asText(""));
        // Envelope + documento no payload (event.data): monta o mesmo token "env/doc" que guardamos.
        JsonNode data = corpo.path("event").path("data");
        String envId = primeiro(data.path("envelope").path("key").asText(null),
                data.path("envelope").path("id").asText(null), data.path("account").path("key").asText(null));
        String docId = primeiro(data.path("document").path("key").asText(null),
                data.path("document").path("id").asText(null), data.path("key").asText(null));
        List<String> tokens = (envId != null && docId != null) ? List.of(token(envId, docId)) : List.of();
        TipoEvento tipo;
        if (nome.contains("closed") || nome.contains("finished") || nome.equals("auto_close") || nome.equals("close")) {
            tipo = TipoEvento.ASSINADO;
        } else if (nome.contains("refus") || nome.contains("cancel")) {
            tipo = TipoEvento.RECUSADO;
        } else {
            tipo = TipoEvento.OUTRO;
        }
        return new EventoWebhook(true, tipo, tokens);
    }

    // ---- helpers ----

    private static String token(String envelopeId, String documentId) {
        return envelopeId + "/" + documentId;
    }

    private static String[] separar(String token) {
        if (token == null) {
            return null;
        }
        int i = token.indexOf('/');
        if (i <= 0 || i >= token.length() - 1) {
            return null;
        }
        return new String[] { token.substring(0, i), token.substring(i + 1) };
    }

    private byte[] baixarModelo(TermoParaAssinar t) {
        if (t.modeloUrl() == null || t.modeloUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Termo indisponível para assinatura (modelo removido).");
        }
        byte[] bytes = storageService.baixarBytes(t.modeloUrl());
        if (bytes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível ler o modelo do termo.");
        }
        return bytes;
    }

    private boolean hmacValido(String assinaturaRecebida, String rawBody, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()
                || assinaturaRecebida == null || assinaturaRecebida.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8));
            String hex = hex(h);
            String recebido = assinaturaRecebida.trim();
            if (recebido.startsWith("sha256=")) {
                recebido = recebido.substring(7);
            }
            return recebido.equalsIgnoreCase(hex);
        } catch (Exception e) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String header(Map<String, String> headers, String nome) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(nome)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
