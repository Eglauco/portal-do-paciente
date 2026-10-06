package com.example.pop.zapsign;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.assinatura.ProvedorAssinatura;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementação {@link AssinaturaProvider} para a ZapSign (REST): Modelos com {{variáveis}} (data[]),
 * lote via documentos EXTRAS no mesmo sign_url, webhook autenticado por header secreto (sem HMAC).
 * Adapta o {@link ZapSignClient} de baixo nível para a interface neutra do POP.
 */
@Component
public class ZapSignProvider implements AssinaturaProvider {

    private final ZapSignClient zapSign;
    private final ConfiguracaoService configuracaoService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ZapSignProvider(ZapSignClient zapSign, ConfiguracaoService configuracaoService) {
        this.zapSign = zapSign;
        this.configuracaoService = configuracaoService;
    }

    @Override
    public ProvedorAssinatura id() {
        return ProvedorAssinatura.ZAPSIGN;
    }

    @Override
    public boolean disponivel() {
        return zapSign.temToken();
    }

    @Override
    public ResultadoTeste testarConexao() {
        try {
            zapSign.testar();
            return new ResultadoTeste(true, "Conexão OK — credenciais válidas.");
        } catch (RuntimeException e) {
            return new ResultadoTeste(false, AssinaturaProvider.mensagemErro(e));
        }
    }

    @Override
    public boolean usaModeloRemoto() {
        return true;
    }

    @Override
    public String registrarModelo(String nome, byte[] docxBytes) {
        return zapSign.criarTemplate(nome, Base64.getEncoder().encodeToString(docxBytes));
    }

    @Override
    public List<DocumentoAssinatura> criar(List<TermoParaAssinar> termos, Signatario s, boolean combinar) {
        // ZapSign: 1 principal + N extras, todos no MESMO sign_url; cada termo com seu próprio docToken.
        List<DocumentoAssinatura> docs = new ArrayList<>();
        TermoParaAssinar p = termos.get(0);
        ZapSignClient.DocumentoCriado principal = zapSign.criarDocViaModelo(
                p.providerTemplateToken(), signatario(s), campos(p.variaveis()), "termo-" + p.termoId());
        docs.add(new DocumentoAssinatura(List.of(p.termoId()), principal.docToken(), principal.signerToken(),
                principal.signUrl()));
        for (int i = 1; i < termos.size(); i++) {
            TermoParaAssinar e = termos.get(i);
            String extraToken = zapSign.anexarDocExtra(principal.docToken(), e.providerTemplateToken(), campos(e.variaveis()));
            docs.add(new DocumentoAssinatura(List.of(e.termoId()), extraToken, null, principal.signUrl()));
        }
        return docs;
    }

    @Override
    public List<ArquivoAssinado> coletarAssinados(String webhookDocToken) {
        List<ArquivoAssinado> out = new ArrayList<>();
        JsonNode doc = zapSign.detalhar(webhookDocToken);
        // Só coleta quando TODOS os signatários assinaram (senão, na coassinatura, o doc parcial — só o
        // paciente — seria baixado e marcado ASSINADO indevidamente). Robusto: checa cada signer, não o status.
        if (!todosAssinaram(doc)) {
            return out;
        }
        adicionar(out, webhookDocToken, doc.path("signed_file").asText(null));
        // Lote: os extra_docs[] trazem token + signed_file (sem "status") — processa cada um pelo signed_file.
        JsonNode extras = doc.path("extra_docs");
        if (extras.isArray()) {
            for (JsonNode e : extras) {
                adicionar(out, e.path("token").asText(null), e.path("signed_file").asText(null));
            }
        }
        return out;
    }

    /** true se o documento tem signatários e TODOS já assinaram (documento concluído). */
    private static boolean todosAssinaram(JsonNode doc) {
        JsonNode signers = doc.path("signers");
        if (!signers.isArray() || signers.isEmpty()) {
            return false;
        }
        for (JsonNode s : signers) {
            if (!assinou(s)) {
                return false;
            }
        }
        return true;
    }

    /** Status de signatário assinado. A API da ZapSign devolve em INGLÊS ("signed"); aceita "assinou" por garantia. */
    private static boolean assinou(JsonNode signer) {
        String st = signer.path("status").asText("");
        return "signed".equals(st) || "assinou".equals(st);
    }

    private void adicionar(List<ArquivoAssinado> out, String token, String signedFile) {
        if (token == null || token.isBlank() || signedFile == null || signedFile.isBlank()) {
            return;
        }
        out.add(new ArquivoAssinado(token, zapSign.baixarArquivo(signedFile)));
    }

    @Override
    public EventoWebhook parseWebhook(Map<String, String> headers, String rawBody) {
        String recebido = header(headers, "X-Zapsign-Secret");
        String webhookSecret = configuracaoService.lerSegredo(ChaveConfiguracao.ZAPSIGN_WEBHOOK_SECRET);
        boolean autentico = webhookSecret != null && !webhookSecret.isBlank() && webhookSecret.equals(recebido);
        if (!autentico) {
            return new EventoWebhook(false, TipoEvento.OUTRO, List.of());
        }
        String eventType;
        String token;
        try {
            JsonNode corpo = mapper.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
            eventType = corpo.path("event_type").asText("");
            token = corpo.path("token").isNull() ? null : corpo.path("token").asText(null);
        } catch (Exception ex) {
            return new EventoWebhook(true, TipoEvento.OUTRO, List.of());
        }
        TipoEvento tipo = switch (eventType) {
            case "doc_signed" -> TipoEvento.ASSINADO;
            case "doc_refused" -> TipoEvento.RECUSADO;
            default -> TipoEvento.OUTRO;
        };
        return new EventoWebhook(true, tipo, token == null || token.isBlank() ? List.of() : List.of(token));
    }

    @Override
    public boolean suportaCoassinatura() {
        return true;
    }

    @Override
    public boolean suportaCoassinaturaCertificado() {
        return true; // ZapSign: certificadoDigital (qualificada ICP) suportada na coassinatura
    }

    @Override
    public Coassinante adicionarCoassinante(String providerDocToken, Signatario coSignatario,
            boolean usarCertificado) {
        // certificadoDigital = assinatura qualificada ICP (o profissional é o último a assinar — ok na coassinatura).
        String authMode = usarCertificado ? "certificadoDigital" : "assinaturaTela";
        ZapSignClient.Signatario s = new ZapSignClient.Signatario(coSignatario.nome(), coSignatario.cpf(),
                coSignatario.phoneCountry(), coSignatario.phoneNumber(), authMode,
                coSignatario.externalId(), "Profissional de saúde", false);
        ZapSignClient.SignatarioAdicionado add = zapSign.adicionarSignatario(providerDocToken, s);
        return new Coassinante(add.signerToken(), add.signUrl());
    }

    @Override
    public boolean signatarioConcluiu(String providerDocToken, String providerSignerId) {
        if (providerSignerId == null || providerSignerId.isBlank()) {
            return false;
        }
        JsonNode doc = zapSign.detalhar(providerDocToken);
        JsonNode signers = doc.path("signers");
        if (signers.isArray()) {
            for (JsonNode s : signers) {
                if (providerSignerId.equals(s.path("token").asText(null))) {
                    return assinou(s);
                }
            }
        }
        return false;
    }

    private static ZapSignClient.Signatario signatario(Signatario s) {
        return new ZapSignClient.Signatario(s.nome(), s.cpf(), s.phoneCountry(), s.phoneNumber(),
                "assinaturaTela", s.externalId(), "Paciente", false);
    }

    private static List<ZapSignClient.CampoModelo> campos(Map<String, String> variaveis) {
        List<ZapSignClient.CampoModelo> lista = new ArrayList<>();
        if (variaveis != null) {
            variaveis.forEach((de, para) -> lista.add(new ZapSignClient.CampoModelo(de, para)));
        }
        return lista;
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
}
