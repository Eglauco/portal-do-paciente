package com.example.pop.zapsign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
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

import com.example.pop.prontuario.AssinaturaTermoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final ZapSignClient zapSign;
    private final AssinaturaTermoService assinaturaTermoService;
    private final String segredo;
    private final ObjectMapper mapper = new ObjectMapper();
    /** Buffer em memória dos webhooks recebidos (só para inspeção na POC). */
    private final Deque<EventoWebhook> eventos = new ConcurrentLinkedDeque<>();

    public ZapSignPocController(ZapSignClient zapSign, AssinaturaTermoService assinaturaTermoService,
            @Value("${zapsign.webhook-secret}") String segredo) {
        this.zapSign = zapSign;
        this.assinaturaTermoService = assinaturaTermoService;
        this.segredo = segredo == null ? "" : segredo;
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
     * Recebe o webhook da ZapSign. Valida o segredo, registra o evento e (para doc_signed com o
     * documento concluído) confirma que o signed_file está presente. Responde 200 rápido; qualquer
     * status != 200 faria a ZapSign reenviar (idempotência ficará no serviço real).
     */
    @PostMapping("/zapsign/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "X-Zapsign-Secret", required = false) String recebido,
            @RequestBody(required = false) String corpoBruto) {
        // Autenticidade: único mecanismo da ZapSign é o header que registramos. 401 não dispara retry.
        if (segredo.isBlank() || !segredo.equals(recebido)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Corpo cru + parse defensivo: um webhook nunca deve responder 500 (500 faria a ZapSign reenviar).
        try {
            JsonNode corpo = mapper.readTree(corpoBruto == null || corpoBruto.isBlank() ? "{}" : corpoBruto);
            String eventType = texto(corpo, "event_type");
            String docToken = texto(corpo, "token");
            String status = texto(corpo, "status");
            boolean temSigned = corpo.hasNonNull("signed_file") && !corpo.get("signed_file").asText().isBlank();
            registrar(new EventoWebhook(LocalDateTime.now(), eventType, docToken, status, temSigned));
            // Fluxo real: ao assinar, processa o termo (idempotente; no-op se o doc não for um termo nosso).
            if ("doc_signed".equals(eventType)) {
                try {
                    assinaturaTermoService.processarAssinado(docToken);
                } catch (RuntimeException e) {
                    registrar(new EventoWebhook(LocalDateTime.now(), "PROC_ERROR:" + e.getClass().getSimpleName(), docToken, status, temSigned));
                }
            } else if ("doc_refused".equals(eventType)) {
                // Recusa: o paciente não concluiu — libera o atendimento para refazer a assinatura.
                try {
                    assinaturaTermoService.processarRecusa(docToken);
                } catch (RuntimeException e) {
                    registrar(new EventoWebhook(LocalDateTime.now(), "RECUSA_ERROR:" + e.getClass().getSimpleName(), docToken, status, temSigned));
                }
            }
        } catch (Exception e) {
            registrar(new EventoWebhook(LocalDateTime.now(), "PARSE_ERROR:" + e.getClass().getSimpleName(), null, null, false));
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
        if (segredo.isBlank() || !segredo.equals(recebido)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo de POC inválido.");
        }
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
