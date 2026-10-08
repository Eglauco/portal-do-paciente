package com.example.pop.sms;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envio de SMS de texto livre via <b>Twilio Messaging API</b> (endpoint {@code Messages.json}),
 * distinto do Twilio Verify (que só manda OTP). Reusa as credenciais da conta
 * ({@code twilio.account-sid}/{@code twilio.auth-token}) e precisa de um remetente:
 * um Messaging Service SID (preferido — o Twilio escolhe o número) ou um número {@code From}
 * em E.164. Sem credenciais ou sem remetente, {@link #configurado()} é {@code false} e nada
 * é enviado. Nunca lança — falhas apenas são logadas (fail-open).
 */
@Service
public class TwilioSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);

    private final RestClient rest;
    private final boolean temCredenciais;
    private final String messagingServiceSid;
    private final String from;

    public TwilioSmsService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.messaging-service-sid:}") String messagingServiceSid,
            @Value("${twilio.sms-from:}") String from) {
        this.temCredenciais = !accountSid.isBlank() && !authToken.isBlank();
        this.messagingServiceSid = messagingServiceSid == null ? "" : messagingServiceSid.trim();
        this.from = normalizarRemetente(from);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + accountSid)
                .requestFactory(factory);
        if (temCredenciais) {
            builder.defaultHeaders(h -> h.setBasicAuth(accountSid, authToken));
        }
        this.rest = builder.build();
    }

    @Override
    public boolean configurado() {
        return temCredenciais && (!messagingServiceSid.isBlank() || !from.isBlank());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean enviar(String telefoneE164, String texto) {
        if (!configurado() || telefoneE164 == null || telefoneE164.isBlank()
                || texto == null || texto.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", telefoneE164);
        body.add("Body", texto);
        // Messaging Service SID é preferido (o Twilio escolhe o número do pool); senão, usa o From.
        if (!messagingServiceSid.isBlank()) {
            body.add("MessagingServiceSid", messagingServiceSid);
        } else {
            body.add("From", from);
        }
        try {
            Map<String, Object> resposta = rest.post().uri("/Messages.json")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String status = resposta == null ? null : String.valueOf(resposta.get("status"));
            log.info("SMS enviado (status={}, to={})", status, mascarar(telefoneE164));
            return true;
        } catch (RestClientException e) {
            log.warn("Falha ao enviar SMS (to={}): {}", mascarar(telefoneE164), e.getMessage());
            return false;
        }
    }

    /** Mascara o telefone para log/auditoria: mantém só os 4 últimos dígitos. */
    private static String mascarar(String e164) {
        return e164 == null || e164.length() < 4 ? "***" : "***" + e164.substring(e164.length() - 4);
    }

    /**
     * Normaliza o remetente: um número só com dígitos vira E.164 (prefixa {@code +}), tolerando
     * quem configura sem o {@code +}. Um Sender ID alfanumérico (tem letras/espaços) fica intacto —
     * nunca recebe {@code +}. Vazio continua vazio.
     */
    private static String normalizarRemetente(String valor) {
        String v = valor == null ? "" : valor.trim();
        return v.matches("\\d{6,}") ? "+" + v : v;
    }
}
