package com.example.pop.docusign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.prontuario.AssinaturaTermoService;

/**
 * Recebe o webhook (DocuSign Connect) de envelope concluído/recusado, valida o HMAC-SHA256 (header
 * {@code X-DocuSign-Signature-1}, Base64) e delega ao serviço de assinatura (independente de provedor). O
 * Connect é configurado por envelope ({@code eventNotification}) ou na conta; a chave HMAC é a
 * {@code docusign.webhook-secret}. {@code GET /dev/docusign/eventos} (protegido por segredo) mostra os últimos
 * payloads e {@code GET /dev/docusign/status} reporta se o provedor está configurado + a URL de consentimento.
 */
@RestController
@RequestMapping
public class DocusignWebhookController {

    private static final int MAX_EVENTOS = 30;
    private static final int MAX_BODY = 4000;

    private final DocusignProvider provider;
    private final DocusignClient client;
    private final AssinaturaTermoService assinaturaTermoService;
    private final String segredo;
    private final Deque<EventoWebhook> eventos = new ConcurrentLinkedDeque<>();

    public DocusignWebhookController(DocusignProvider provider, DocusignClient client,
            AssinaturaTermoService assinaturaTermoService,
            @Value("${docusign.webhook-secret:}") String segredo) {
        this.provider = provider;
        this.client = client;
        this.assinaturaTermoService = assinaturaTermoService;
        this.segredo = segredo == null ? "" : segredo;
    }

    public record EventoWebhook(LocalDateTime recebidoEm, boolean autentico, String tipo, String docTokens,
            String corpo) {
    }

    @PostMapping("/docusign/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String corpoBruto) {
        AssinaturaProvider.EventoWebhook ev = provider.parseWebhook(headers, corpoBruto);
        registrar(new EventoWebhook(LocalDateTime.now(), ev.autentico(), ev.tipo().name(),
                String.join(",", ev.docTokens()), truncar(corpoBruto)));
        if (!ev.autentico()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            if (ev.tipo() == AssinaturaProvider.TipoEvento.ASSINADO) {
                assinaturaTermoService.processarAssinados(provider, ev.docTokens());
            } else if (ev.tipo() == AssinaturaProvider.TipoEvento.RECUSADO) {
                assinaturaTermoService.processarRecusa(ev.docTokens());
            }
        } catch (RuntimeException e) {
            registrar(new EventoWebhook(LocalDateTime.now(), true, "PROC_ERROR:" + e.getClass().getSimpleName(),
                    null, null));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev/docusign/eventos")
    public List<EventoWebhook> eventos(@RequestHeader(value = "X-Poc-Secret", required = false) String poc) {
        exigirSegredo(poc);
        return new ArrayList<>(eventos);
    }

    /** Diagnóstico dev: provedor configurado? + URL de consentimento (uma vez) do JWT Grant. */
    @GetMapping("/dev/docusign/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Poc-Secret", required = false) String poc,
            @org.springframework.web.bind.annotation.RequestParam(value = "redirect", required = false) String redirect) {
        exigirSegredo(poc);
        String r = redirect == null || redirect.isBlank() ? "https://developers.docusign.com/platform/auth/consent"
                : redirect;
        String consent = client.oauthBase() + "/oauth/auth?response_type=code&scope="
                + client.scope().replace(" ", "%20") + "&client_id=" + client.integrationKey()
                + "&redirect_uri=" + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
        return Map.of("configurado", provider.disponivel(), "urlConsentimento", consent);
    }

    private void exigirSegredo(String poc) {
        if (segredo.isBlank() || !segredo.equals(poc)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo inválido.");
        }
    }

    private void registrar(EventoWebhook e) {
        eventos.addFirst(e);
        while (eventos.size() > MAX_EVENTOS) {
            eventos.pollLast();
        }
    }

    private static String truncar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_BODY ? s : s.substring(0, MAX_BODY) + "…";
    }
}
