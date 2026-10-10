package com.example.pop.clicksign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.pop.assinatura.ProvedorAssinatura;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.prontuario.AssinaturaTermoService;
import com.example.pop.prontuario.TermoAssinatura;
import com.example.pop.prontuario.TermoAssinaturaRepository;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Recebe o webhook da Clicksign (evento de documento fechado/assinado ou recusa), valida o HMAC-SHA256
 * (header {@code Content-Hmac: sha256=<hex>}) e delega ao serviço de assinatura. Registro do webhook é
 * feito por API (POST /api/v3/webhooks, que devolve o secret). {@code GET /dev/clicksign/eventos} mostra
 * os últimos payloads (protegido por segredo) para inspecionar o formato real nos testes.
 */
@RestController
@RequestMapping
public class ClicksignWebhookController {

    private static final int MAX_EVENTOS = 30;
    private static final int MAX_BODY = 4000;
    private static final Logger log = LoggerFactory.getLogger(ClicksignWebhookController.class);

    private final ClicksignProvider provider;
    private final ClicksignClient client;
    private final AssinaturaTermoService assinaturaTermoService;
    private final TermoAssinaturaRepository termoRepository;
    private final ConfiguracaoService configuracaoService;
    private final Deque<EventoWebhook> eventos = new ConcurrentLinkedDeque<>();

    public ClicksignWebhookController(ClicksignProvider provider, ClicksignClient client,
            AssinaturaTermoService assinaturaTermoService, TermoAssinaturaRepository termoRepository,
            ConfiguracaoService configuracaoService) {
        this.provider = provider;
        this.client = client;
        this.assinaturaTermoService = assinaturaTermoService;
        this.termoRepository = termoRepository;
        this.configuracaoService = configuracaoService;
    }

    public record EventoWebhook(LocalDateTime recebidoEm, boolean autentico, String tipo, String docTokens,
            String corpo) {
    }

    @PostMapping("/clicksign/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String corpoBruto) {
        // Multi-inquilino: o webhook chega SEM JWT (tenant em public). Guarda anti-crash (ver memória
        // assinatura-webhooks-multi-inquilino): se parseWebhook/processar tocar config/domínio do public
        // (ausente após o drop), não derruba o app nem pede retry — loga e responde 200.
        try {
            AssinaturaProvider.EventoWebhook ev = provider.parseWebhook(headers, corpoBruto);
            registrar(new EventoWebhook(LocalDateTime.now(), ev.autentico(), ev.tipo().name(),
                    String.join(",", ev.docTokens()), truncar(corpoBruto)));
            if (!ev.autentico()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (ev.tipo() == AssinaturaProvider.TipoEvento.ASSINADO) {
                assinaturaTermoService.processarAssinados(provider, ev.docTokens());
            } else if (ev.tipo() == AssinaturaProvider.TipoEvento.RECUSADO) {
                assinaturaTermoService.processarRecusa(ev.docTokens());
            }
        } catch (RuntimeException e) {
            log.warn("Webhook Clicksign falhou (ignorado): {}", e.toString());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev/clicksign/eventos")
    public List<EventoWebhook> eventos(@RequestHeader(value = "X-Poc-Secret", required = false) String poc) {
        String segredo = configuracaoService.lerSegredo(ChaveConfiguracao.CLICKSIGN_WEBHOOK_SECRET);
        if (segredo == null || segredo.isBlank() || !segredo.equals(poc)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo inválido.");
        }
        return new ArrayList<>(eventos);
    }

    /**
     * DEV (aberto, localhost — REMOVER antes de produção): inspeciona os termos recentes do Clicksign, batendo
     * no Clicksign para mostrar o status REAL do envelope e do documento. Ajuda a diagnosticar o ERRO 500 do
     * widget (ex.: envelope fora de "running", documento ainda em processamento, ou id/ambiente errados).
     */
    @GetMapping("/dev/clicksign/inspecionar")
    public List<Map<String, Object>> inspecionar(@RequestHeader(value = "X-Poc-Secret", required = false) String poc) {
        // Gate de segredo (mesmo de eventos()): o endpoint bate AO VIVO na Clicksign e devolve PII dos
        // signatários (nome/CPF/e-mail) — NUNCA pode ficar anônimo (/dev/clicksign/** é permitAll).
        String segredo = configuracaoService.lerSegredo(ChaveConfiguracao.CLICKSIGN_WEBHOOK_SECRET);
        if (segredo == null || segredo.isBlank() || !segredo.equals(poc)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo inválido.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (TermoAssinatura t : termoRepository.findTop10ByProvedorOrderByIdDesc(ProvedorAssinatura.CLICKSIGN)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("termoId", t.getId());
            m.put("nome", t.getNome());
            m.put("statusPop", t.getStatus() == null ? null : t.getStatus().name());
            m.put("providerDocToken", t.getProviderDocToken());
            m.put("signerId", t.getProviderSignerId());
            String token = t.getProviderDocToken();
            if (token != null && token.contains("/")) {
                String env = token.substring(0, token.indexOf('/'));
                String doc = token.substring(token.indexOf('/') + 1);
                m.put("env", env);
                m.put("doc", doc);
                try {
                    JsonNode envAttrs = client.detalharEnvelope(env);
                    m.put("envelopeStatus", envAttrs.path("status").asText(null));
                } catch (RuntimeException e) {
                    m.put("envelopeErro", e.getMessage());
                }
                try {
                    JsonNode docAttrs = client.detalharDocumento(env, doc);
                    m.put("documentStatus", docAttrs.path("status").asText(null));
                    m.put("documentoRaw", client.getRaw("/envelopes/" + env + "/documents/" + doc));
                } catch (RuntimeException e) {
                    m.put("documentErro", e.getMessage());
                }
                try {
                    m.put("requisitosRaw", client.getRaw("/envelopes/" + env + "/requirements"));
                } catch (RuntimeException e) {
                    m.put("requisitosErro", e.getMessage());
                }
                try {
                    m.put("eventosRaw", client.getRaw("/envelopes/" + env + "/events"));
                } catch (RuntimeException e) {
                    m.put("eventosErro", e.getMessage());
                }
                try {
                    m.put("signatarioRaw", client.getRaw("/envelopes/" + env + "/signers/" + t.getProviderSignerId()));
                } catch (RuntimeException e) {
                    m.put("signatarioErro", e.getMessage());
                }
            }
            out.add(m);
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
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
