package com.example.pop.autentique;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

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
import com.example.pop.prontuario.StatusTermoAssinatura;
import com.example.pop.prontuario.TermoAssinatura;
import com.example.pop.prontuario.TermoAssinaturaRepository;

/**
 * Recebe o webhook da Autentique ({@code signature.accepted} / {@code signature.rejected} / {@code
 * document.finished}), valida o HMAC-SHA256 ({@code X-Autentique-Signature}) e delega ao serviço de
 * assinatura (independente de provedor). Registro do webhook é feito no Painel de Desenvolvedor da
 * Autentique (não por API). Responde 200 rápido; 401 (HMAC inválido) não deve disparar retry.
 *
 * <p>{@code GET /dev/autentique/eventos} (protegido por segredo) mostra os últimos payloads recebidos —
 * ajuda a inspecionar o formato real durante os testes.
 */
@RestController
@RequestMapping
public class AutentiqueWebhookController {

    private static final int MAX_EVENTOS = 30;
    private static final int MAX_BODY = 4000;

    private final AutentiqueProvider provider;
    private final AutentiqueClient client;
    private final AssinaturaTermoService assinaturaTermoService;
    private final TermoAssinaturaRepository termoRepository;
    private final ConfiguracaoService configuracaoService;
    private final Deque<EventoWebhook> eventos = new ConcurrentLinkedDeque<>();

    public AutentiqueWebhookController(AutentiqueProvider provider, AutentiqueClient client,
            AssinaturaTermoService assinaturaTermoService, TermoAssinaturaRepository termoRepository,
            ConfiguracaoService configuracaoService) {
        this.provider = provider;
        this.client = client;
        this.assinaturaTermoService = assinaturaTermoService;
        this.termoRepository = termoRepository;
        this.configuracaoService = configuracaoService;
    }

    /** Gate dos endpoints /dev: exige o webhook-secret da Autentique (lido do banco). */
    private void exigirSegredo(String recebido) {
        String segredo = configuracaoService.lerSegredo(ChaveConfiguracao.AUTENTIQUE_WEBHOOK_SECRET);
        if (segredo == null || segredo.isBlank() || !segredo.equals(recebido)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo inválido.");
        }
    }

    /** Payload guardado para inspeção (dev). */
    public record EventoWebhook(LocalDateTime recebidoEm, boolean autentico, String tipo, String docTokens,
            String corpo) {
    }

    @PostMapping("/autentique/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String corpoBruto) {
        AssinaturaProvider.EventoWebhook ev = provider.parseWebhook(headers, corpoBruto);
        // Registra para inspeção (mesmo quando não-autêntico, para depurar o HMAC/payload no 1º teste).
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

    @GetMapping("/dev/autentique/eventos")
    public List<EventoWebhook> eventos(@RequestHeader(value = "X-Poc-Secret", required = false) String poc) {
        exigirSegredo(poc);
        return new ArrayList<>(eventos);
    }

    /**
     * DEV: inspeciona a COASSINATURA na Autentique. Lista cada termo em AGUARDANDO_PROFISSIONAL do provedor
     * Autentique com o que está GUARDADO (ids/URL do paciente vs profissional) + os signatários AO VIVO na
     * Autentique (public_id, nome, se assinou, short_link). Permite comparar o link guardado do profissional
     * com quem realmente é cada signatário. Só campos escalares do termo (sem associação LAZY).
     */
    // Inspetor DEV da coassinatura (secreto). Dev-only; remover junto com os demais /dev antes de produção.
    @GetMapping("/dev/autentique/coassinatura")
    public List<Map<String, Object>> coassinatura(
            @RequestHeader(value = "X-Poc-Secret", required = false) String poc,
            @org.springframework.web.bind.annotation.RequestParam(value = "secret", required = false) String secretQuery) {
        exigirSegredo(poc != null ? poc : secretQuery);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TermoAssinatura t : termoRepository.findByStatus(StatusTermoAssinatura.AGUARDANDO_PROFISSIONAL)) {
            if (t.getProvedor() != ProvedorAssinatura.AUTENTIQUE) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("termoId", t.getId());
            m.put("nome", t.getNome());
            m.put("documentId", t.getProviderDocToken());
            m.put("pacienteSignerId", t.getProviderSignerId());
            m.put("profissionalSignerId", t.getProviderSignerIdProfissional());
            m.put("profissionalSignUrl", t.getSignUrlProfissional());
            try {
                m.put("autentiqueAoVivo", client.detalharRaw(t.getProviderDocToken()));
            } catch (RuntimeException e) {
                m.put("erro", e.getMessage());
            }
            out.add(m);
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
