package com.example.pop.autentique;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 * Implementação {@link AssinaturaProvider} para a Autentique (GraphQL). Como a Autentique NÃO substitui
 * variáveis nem tem "modelo remoto", o POP renderiza o Word (.docx) em HTML com os valores ({@link
 * RenderizadorDocumento}) e sobe o arquivo pronto. Lote: {@code combinar=true} junta os termos num único
 * documento; senão cria um documento por termo (short_link por termo). Webhook autenticado por HMAC-SHA256.
 */
@Component
public class AutentiqueProvider implements AssinaturaProvider {

    private final AutentiqueClient client;
    private final RenderizadorDocumento renderizador;
    private final StorageService storageService;
    private final ConfiguracaoService configuracaoService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AutentiqueProvider(AutentiqueClient client, RenderizadorDocumento renderizador,
            StorageService storageService, ConfiguracaoService configuracaoService) {
        this.client = client;
        this.renderizador = renderizador;
        this.storageService = storageService;
        this.configuracaoService = configuracaoService;
    }

    @Override
    public ProvedorAssinatura id() {
        return ProvedorAssinatura.AUTENTIQUE;
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
        return false; // renderiza o documento localmente a partir do .docx
    }

    @Override
    public String registrarModelo(String nome, byte[] docxBytes) {
        return null; // sem template remoto na Autentique
    }

    @Override
    public List<DocumentoAssinatura> criar(List<TermoParaAssinar> termos, Signatario s, boolean combinar) {
        List<DocumentoAssinatura> docs = new ArrayList<>();
        if (combinar && termos.size() > 1) {
            // Um único .docx com todos os termos (uma assinatura cobre todos).
            List<byte[]> preenchidos = new ArrayList<>();
            for (TermoParaAssinar t : termos) {
                preenchidos.add(renderizador.preencherDocx(baixarModelo(t), t.variaveis()));
            }
            byte[] docx = renderizador.combinarDocx(preenchidos);
            AutentiqueClient.DocumentoCriado doc = client.criarDocumento(
                    "Termos de Consentimento", docx, CONTENT_TYPE_DOCX, "termos.docx", s);
            docs.add(new DocumentoAssinatura(termos.stream().map(TermoParaAssinar::termoId).toList(),
                    doc.documentId(), doc.signerId(), doc.shortLink()));
        } else {
            // Um .docx por termo (short_link por termo).
            for (TermoParaAssinar t : termos) {
                byte[] docx = renderizador.preencherDocx(baixarModelo(t), t.variaveis());
                AutentiqueClient.DocumentoCriado doc = client.criarDocumento(
                        t.nome(), docx, CONTENT_TYPE_DOCX, "termo.docx", s);
                docs.add(new DocumentoAssinatura(List.of(t.termoId()), doc.documentId(), doc.signerId(), doc.shortLink()));
            }
        }
        return docs;
    }

    private static final String CONTENT_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public List<ArquivoAssinado> coletarAssinados(String webhookDocToken) {
        String url = client.urlAssinado(webhookDocToken);
        if (url == null) {
            return List.of();
        }
        return List.of(new ArquivoAssinado(webhookDocToken, client.baixar(url)));
    }

    @Override
    public EventoWebhook parseWebhook(Map<String, String> headers, String rawBody) {
        String webhookSecret = configuracaoService.lerSegredo(ChaveConfiguracao.AUTENTIQUE_WEBHOOK_SECRET);
        boolean autentico = hmacValido(header(headers, "X-Autentique-Signature"), rawBody, webhookSecret);
        if (!autentico) {
            return new EventoWebhook(false, TipoEvento.OUTRO, List.of());
        }
        JsonNode corpo;
        try {
            corpo = mapper.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (Exception e) {
            return new EventoWebhook(true, TipoEvento.OUTRO, List.of());
        }
        String tipo = primeiroNaoVazio(
                corpo.path("event").path("type").asText(null),
                corpo.path("type").asText(null));
        String docId = primeiroNaoVazio(
                corpo.path("event").path("data").path("document").asText(null), // eventos de ASSINATURA
                corpo.path("event").path("data").path("object").path("id").asText(null), // eventos de DOCUMENTO
                corpo.path("data").path("document").asText(null),
                corpo.path("data").path("object").path("id").asText(null),
                corpo.path("document").path("id").asText(null));
        TipoEvento t;
        if (tipo == null) {
            t = TipoEvento.OUTRO;
        } else if (tipo.contains("accepted") || tipo.contains("finished") || tipo.contains("signed")) {
            t = TipoEvento.ASSINADO;
        } else if (tipo.contains("rejected") || tipo.contains("refused")) {
            t = TipoEvento.RECUSADO;
        } else {
            t = TipoEvento.OUTRO;
        }
        return new EventoWebhook(true, t, docId == null || docId.isBlank() ? List.of() : List.of(docId));
    }

    // ---------- Coassinatura do profissional (2º signatário, após o paciente) ----------

    @Override
    public boolean suportaCoassinatura() {
        return true;
    }

    @Override
    public boolean cerimoniaCoassinaturaEmbutivel() {
        // A página de assinatura (painel.autentique.com.br) NÃO funciona em iframe cross-origin: o navegador
        // bloqueia os cookies de sessão (terceiros) e a cerimônia quebra ("você já assinou"). Abrir em ABA.
        return false;
    }

    @Override
    public Coassinante adicionarCoassinante(String providerDocToken, Signatario coSignatario, boolean usarCertificado) {
        // createSigner name-only → short_link DURÁVEL do profissional. A ordem (paciente→profissional) é
        // garantida pela máquina de estados do POP (o link só é exposto após o paciente assinar). Certificado
        // (qualified) é por documento na Autentique e não se aplica a signatário anexado — já bloqueado no serviço.
        AutentiqueClient.DocumentoCriado add =
                client.adicionarSignatario(providerDocToken, coSignatario.nome(), coSignatario.email());
        return new Coassinante(add.signerId(), add.shortLink());
    }

    @Override
    public boolean signatarioConcluiu(String providerDocToken, String providerSignerId) {
        return client.signatarioAssinou(providerDocToken, providerSignerId);
    }

    // ---- helpers ----

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
            String base64 = Base64.getEncoder().encodeToString(h);
            String recebido = assinaturaRecebida.trim();
            if (recebido.startsWith("sha256=")) {
                recebido = recebido.substring(7);
            }
            return recebido.equalsIgnoreCase(hex) || recebido.equals(base64);
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

    private static String primeiroNaoVazio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
