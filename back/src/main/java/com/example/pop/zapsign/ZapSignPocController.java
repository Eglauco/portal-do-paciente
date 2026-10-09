package com.example.pop.zapsign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.prontuario.AssinaturaTermoService;

/**
 * POC da integração ZapSign (assinatura eletrônica do TCLE) — SOMENTE para validar o fluxo em
 * SANDBOX localmente. NÃO é o fluxo produtivo (não persiste em banco, não amarra ao prontuário).
 * Remover/replantar como serviço real na Fase 2.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /dev/zapsign/criar} — cria um documento e devolve o sign_url (protegido por
 *       segredo de POC no header X-Poc-Secret).</li>
 *   <li>{@code POST /zapsign/webhook} — recebe o callback da ZapSign (fonte de verdade). Valida o
 *       header X-Zapsign-Secret (único mecanismo de autenticidade — a ZapSign não assina HMAC).</li>
 *   <li>{@code GET /dev/zapsign/eventos} e {@code /dev/zapsign/detalhar/{docToken}} — inspeção.</li>
 * </ul>
 */
@RestController
@RequestMapping
public class ZapSignPocController {

    private static final int MAX_EVENTOS = 50;
    private static final Logger log = LoggerFactory.getLogger(ZapSignPocController.class);

    private final ZapSignClient zapSign;
    private final ZapSignProvider zapSignProvider;
    private final AssinaturaTermoService assinaturaTermoService;
    private final ConfiguracaoService configuracaoService;
    /** Buffer em memória dos webhooks recebidos (só para inspeção na POC). */
    private final Deque<EventoWebhook> eventos = new ConcurrentLinkedDeque<>();

    public ZapSignPocController(ZapSignClient zapSign, ZapSignProvider zapSignProvider,
            AssinaturaTermoService assinaturaTermoService, ConfiguracaoService configuracaoService) {
        this.zapSign = zapSign;
        this.zapSignProvider = zapSignProvider;
        this.assinaturaTermoService = assinaturaTermoService;
        this.configuracaoService = configuracaoService;
    }

    // ------- criar (dev) -------

    public record CriarRequest(String nome, String base64Pdf, String cpf,
            String phoneCountry, String phoneNumber, String authMode, Boolean requireSelfie) {
    }

    @PostMapping("/dev/zapsign/criar")
    public ZapSignClient.DocumentoCriado criar(@RequestHeader(value = "X-Poc-Secret", required = false) String poc,
            @RequestBody(required = false) CriarRequest req) {
        exigirSegredo(poc);
        CriarRequest r = req == null ? new CriarRequest(null, null, null, null, null, null, null) : req;
        // Sem base64Pdf: usa o PDF de amostra do classpath (o app pode disparar sem enviar arquivo).
        String base64 = r.base64Pdf() == null || r.base64Pdf().isBlank() ? amostraBase64() : r.base64Pdf();
        String nome = r.nome() == null || r.nome().isBlank() ? "POC TCLE" : r.nome();
        String authMode = r.authMode() == null || r.authMode().isBlank() ? "assinaturaTela" : r.authMode();
        boolean requireSelfie = Boolean.TRUE.equals(r.requireSelfie());
        ZapSignClient.Signatario signatario = new ZapSignClient.Signatario(
                "Paciente Teste POC", r.cpf(), r.phoneCountry(), r.phoneNumber(),
                authMode, "paciente-poc", "Paciente", requireSelfie);
        return zapSign.criarDocumento(nome, base64, "poc-tcle", signatario);
    }

    /** PDF de amostra (classpath) usado quando o cliente não envia um base64 — só para a POC. */
    private String amostraBase64() {
        try {
            byte[] bytes = new ClassPathResource("zapsign-poc-sample.pdf").getInputStream().readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Amostra de PDF da POC não encontrada.");
        }
    }

    @GetMapping(value = "/dev/zapsign/detalhar/{docToken}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String detalhar(@RequestHeader(value = "X-Poc-Secret", required = false) String poc,
            @PathVariable String docToken) {
        exigirSegredo(poc);
        // JSON cru da ZapSign (retornar JsonNode direto faria o Spring serializar o bean, não a árvore).
        return zapSign.detalharRaw(docToken);
    }

    // ------- webhook (ZapSign -> POP) -------

    /** Payload guardado para inspeção na POC. */
    public record EventoWebhook(LocalDateTime recebidoEm, String eventType, String docToken, String status,
            boolean temSignedFile) {
    }

    /**
     * Recebe o webhook da ZapSign e delega ao {@link ZapSignProvider} (valida o segredo e normaliza o
     * evento) + ao {@link AssinaturaTermoService} (processa assinatura/recusa, independente de provedor).
     * Responde 200 rápido; qualquer status != 200 faria a ZapSign reenviar. 401 (segredo inválido) não retenta.
     */
    @PostMapping("/zapsign/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String corpoBruto) {
        // Multi-inquilino: o webhook chega SEM JWT (tenant em public). Enquanto a resolução por-inquilino
        // do webhook não é feita (ver memória assinatura-webhooks-multi-inquilino), envolve tudo numa guarda:
        // se parseWebhook/processar tocar config/domínio do public (ausente após o drop), não derruba o app
        // nem pede retry — loga e responde 200.
        try {
            AssinaturaProvider.EventoWebhook ev = zapSignProvider.parseWebhook(headers, corpoBruto);
            if (!ev.autentico()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            registrar(new EventoWebhook(LocalDateTime.now(), ev.tipo().name(), String.join(",", ev.docTokens()), null, false));
            if (ev.tipo() == AssinaturaProvider.TipoEvento.ASSINADO) {
                assinaturaTermoService.processarAssinados(zapSignProvider, ev.docTokens());
            } else if (ev.tipo() == AssinaturaProvider.TipoEvento.RECUSADO) {
                assinaturaTermoService.processarRecusa(ev.docTokens());
            }
        } catch (RuntimeException e) {
            log.warn("Webhook ZapSign falhou (ignorado): {}", e.toString());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev/zapsign/eventos")
    public List<EventoWebhook> eventos(@RequestHeader(value = "X-Poc-Secret", required = false) String poc) {
        exigirSegredo(poc);
        return new ArrayList<>(eventos);
    }

    // ------- helpers -------

    private void registrar(EventoWebhook e) {
        eventos.addFirst(e);
        while (eventos.size() > MAX_EVENTOS) {
            eventos.pollLast();
        }
    }

    private void exigirSegredo(String recebido) {
        String segredo = configuracaoService.lerSegredo(ChaveConfiguracao.ZAPSIGN_WEBHOOK_SECRET);
        if (segredo == null || segredo.isBlank() || !segredo.equals(recebido)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo de POC inválido.");
        }
    }
}
