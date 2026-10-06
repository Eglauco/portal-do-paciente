package com.example.pop.docusign;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import com.example.pop.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementação {@link AssinaturaProvider} para o DocuSign (eSignature REST v2.1). Como não há template com
 * variáveis na API, o POP renderiza o .docx local e envia pronto (igual à Autentique/Clicksign). Uma cerimônia
 * = 1 ENVELOPE com 1+ documentos e 1 signatário embutido (uma única URL cobre todos os documentos). No modo
 * COMBINADO um único .docx cobre todos os termos; no SEPARADO cada termo é um documento do mesmo envelope
 * (um PDF assinado por termo). O token guardado é {@code envelopeId/documentId}. Webhook (Connect) por HMAC-SHA256.
 */
@Component
public class DocusignProvider implements AssinaturaProvider {

    private static final String CONTENT_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocusignClient client;
    private final RenderizadorDocumento renderizador;
    private final StorageService storageService;
    private final String webhookSecret;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocusignProvider(DocusignClient client, RenderizadorDocumento renderizador,
            StorageService storageService, @Value("${docusign.webhook-secret:}") String webhookSecret) {
        this.client = client;
        this.renderizador = renderizador;
        this.storageService = storageService;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    @Override
    public ProvedorAssinatura id() {
        return ProvedorAssinatura.DOCUSIGN;
    }

    @Override
    public boolean disponivel() {
        return client.temCredenciais();
    }

    @Override
    public boolean usaModeloRemoto() {
        return false; // renderiza o .docx local e envia pronto (sem template por variáveis na API)
    }

    @Override
    public String registrarModelo(String nome, byte[] docxBytes) {
        return null;
    }

    @Override
    public List<DocumentoAssinatura> criar(List<TermoParaAssinar> termos, Signatario s, boolean combinar) {
        // Monta os documentos do envelope (com a âncora de assinatura injetada) e mapeia documentId -> termo(s).
        List<DocusignClient.DocumentoEnvio> envio = new ArrayList<>();
        List<List<Long>> termosPorDoc = new ArrayList<>();

        if (combinar && termos.size() > 1) {
            List<byte[]> preenchidos = new ArrayList<>();
            for (TermoParaAssinar t : termos) {
                preenchidos.add(renderizador.preencherDocx(baixarModelo(t), t.variaveis()));
            }
            byte[] docx = comAncoras(renderizador.combinarDocx(preenchidos));
            envio.add(new DocusignClient.DocumentoEnvio("1", "termos.docx", docx));
            termosPorDoc.add(termos.stream().map(TermoParaAssinar::termoId).toList());
        } else {
            int i = 1;
            for (TermoParaAssinar t : termos) {
                byte[] docx = comAncoras(renderizador.preencherDocx(baixarModelo(t), t.variaveis()));
                envio.add(new DocusignClient.DocumentoEnvio(String.valueOf(i++), "termo.docx", docx));
                termosPorDoc.add(List.of(t.termoId()));
            }
        }

        String envelopeId = client.criarEnvelope("Termos de Consentimento", envio, s);
        String signUrl = client.urlCerimonia(envelopeId, s); // uma URL cobre todos os documentos

        List<DocumentoAssinatura> docs = new ArrayList<>();
        for (int i = 0; i < envio.size(); i++) {
            String token = token(envelopeId, envio.get(i).documentId());
            // signerId "1" = recipient do paciente (permite a checagem por-recipient na coassinatura).
            docs.add(new DocumentoAssinatura(termosPorDoc.get(i), token, "1", signUrl));
        }
        return docs;
    }

    /** Injeta as âncoras invisíveis do paciente E do profissional (a do profissional só vira campo se houver coassinatura). */
    private byte[] comAncoras(byte[] docx) {
        return renderizador.anexarAncora(
                renderizador.anexarAncora(docx, DocusignClient.ANCORA_ASSINATURA),
                DocusignClient.ANCORA_ASSINATURA_PROFISSIONAL);
    }

    @Override
    public List<ArquivoAssinado> coletarAssinados(String webhookDocToken) {
        String[] partes = separar(webhookDocToken);
        if (partes == null) {
            return List.of();
        }
        String envelopeId = partes[0];
        String documentoFiltro = partes[1]; // null quando o token é só o envelope (veio do webhook)
        if (!"completed".equalsIgnoreCase(client.statusEnvelope(envelopeId))) {
            return List.of(); // ainda não concluído — não baixa
        }
        List<ArquivoAssinado> arquivos = new ArrayList<>();
        for (String documentId : client.documentosDoEnvelope(envelopeId)) {
            if (documentoFiltro != null && !documentoFiltro.equals(documentId)) {
                continue;
            }
            byte[] pdf = client.baixarDocumento(envelopeId, documentId);
            arquivos.add(new ArquivoAssinado(token(envelopeId, documentId), pdf));
        }
        return arquivos;
    }

    @Override
    public EventoWebhook parseWebhook(Map<String, String> headers, String rawBody) {
        boolean autentico = hmacValido(header(headers, "X-DocuSign-Signature-1"), rawBody);
        if (!autentico) {
            return new EventoWebhook(false, TipoEvento.OUTRO, List.of());
        }
        JsonNode corpo;
        try {
            corpo = mapper.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (Exception e) {
            return new EventoWebhook(true, TipoEvento.OUTRO, List.of());
        }
        String evento = corpo.path("event").asText("");
        String envelopeId = primeiro(corpo.path("data").path("envelopeId").asText(null),
                corpo.path("envelopeId").asText(null));
        // Token = só o envelope: coletarAssinados enumera os documentos e monta env/doc para cada termo.
        List<String> tokens = envelopeId == null || envelopeId.isBlank() ? List.of() : List.of(envelopeId);
        TipoEvento tipo;
        String ev = evento.toLowerCase();
        if (ev.contains("completed")) {
            tipo = TipoEvento.ASSINADO;
        } else if (ev.contains("declined") || ev.contains("voided")) {
            tipo = TipoEvento.RECUSADO;
        } else {
            tipo = TipoEvento.OUTRO;
        }
        return new EventoWebhook(true, tipo, tokens);
    }

    // ---------- Coassinatura do profissional (2º recipient, routingOrder 2, após o paciente) ----------

    @Override
    public boolean suportaCoassinatura() {
        return true;
    }

    @Override
    public boolean cerimoniaCoassinaturaEmbutivel() {
        return false; // recipient view é de uso único + depende de headers de frame → o front abre em ABA
    }

    @Override
    public Coassinante adicionarCoassinante(String providerDocToken, Signatario coSignatario, boolean usarCertificado) {
        String[] p = separar(providerDocToken);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Documento do DocuSign inválido para coassinatura.");
        }
        // routingOrder "2" ⇒ o DocuSign só libera o profissional depois do paciente (ordem nativa).
        client.adicionarSignatario(p[0], "2", "2", coSignatario, DocusignClient.ANCORA_ASSINATURA_PROFISSIONAL);
        return new Coassinante("2", null); // URL efêmera — gerada sob demanda em urlCoassinante()
    }

    @Override
    public boolean signatarioConcluiu(String providerDocToken, String providerSignerId) {
        String[] p = separar(providerDocToken);
        if (p == null || providerSignerId == null || providerSignerId.isBlank()) {
            return false;
        }
        return client.recipienteConcluiu(p[0], providerSignerId);
    }

    @Override
    public String urlCoassinante(String providerDocToken, String providerSignerIdProfissional,
            Signatario coSignatario, String signUrlArmazenada) {
        String[] p = separar(providerDocToken);
        if (p == null || coSignatario == null) {
            return signUrlArmazenada;
        }
        String recipientId = providerSignerIdProfissional == null || providerSignerIdProfissional.isBlank()
                ? "2" : providerSignerIdProfissional;
        return client.urlCerimoniaRecipiente(p[0], recipientId, coSignatario);
    }

    @Override
    public String regenerarUrlPaciente(String providerDocToken, Signatario paciente, String signUrlAtual) {
        // Adicionar o profissional invalidou a recipient view do paciente — gera uma nova (recipient "1").
        String[] p = separar(providerDocToken);
        if (p == null || paciente == null) {
            return signUrlAtual;
        }
        return client.urlCerimonia(p[0], paciente);
    }

    // ---- helpers ----

    private static String token(String envelopeId, String documentId) {
        return envelopeId + "/" + documentId;
    }

    /** Separa "env/doc" (documento específico) ou "env" (só o envelope → documento null). */
    private static String[] separar(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int i = token.indexOf('/');
        if (i < 0) {
            return new String[] { token, null }; // só o envelope (veio do webhook)
        }
        if (i == 0 || i >= token.length() - 1) {
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

    /** DocuSign Connect assina o corpo com HMAC-SHA256 e envia em Base64 no header X-DocuSign-Signature-1. */
    private boolean hmacValido(String assinaturaRecebida, String rawBody) {
        if (webhookSecret.isBlank() || assinaturaRecebida == null || assinaturaRecebida.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(h);
            return assinaturaRecebida.trim().equals(base64);
        } catch (Exception e) {
            return false;
        }
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

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
