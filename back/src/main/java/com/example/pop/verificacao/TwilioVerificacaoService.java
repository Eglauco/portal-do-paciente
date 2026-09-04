package com.example.pop.verificacao;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificação por OTP via <b>Twilio Verify</b> (WhatsApp + SMS). O Twilio gera,
 * envia e valida o código — o backend NÃO guarda código nem hash. As credenciais
 * vêm de variáveis de ambiente; sem elas, o serviço falha de forma segura (503),
 * nunca liberando acesso.
 */
@Service
public class TwilioVerificacaoService implements VerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(TwilioVerificacaoService.class);

    private final RestClient rest;
    private final boolean configurado;
    /** Idioma do OTP (mensagem em pt-BR e casa com o template de WhatsApp aprovado em pt_BR). */
    private final String locale;

    public TwilioVerificacaoService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.verify-service-sid:}") String serviceSid,
            @Value("${twilio.verify-locale:pt-BR}") String locale) {
        this.configurado = !accountSid.isBlank() && !authToken.isBlank() && !serviceSid.isBlank();
        this.locale = locale;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://verify.twilio.com/v2/Services/" + serviceSid)
                .requestFactory(factory);
        if (configurado) {
            builder.defaultHeaders(h -> h.setBasicAuth(accountSid, authToken));
        }
        this.rest = builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enviar(String telefoneE164, CanalVerificacao canal) {
        exigirConfig();
        String canalSolicitado = canal == CanalVerificacao.WHATSAPP ? "whatsapp" : "sms";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", telefoneE164);
        body.add("Channel", canalSolicitado);
        body.add("Locale", locale);
        try {
            Map<String, Object> resposta = rest.post().uri("/Verifications")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            // Diagnóstico: canal que PEDIMOS × canal que o Twilio ACEITOU. Sem o código (só status/canal).
            String canalTwilio = resposta == null ? null : String.valueOf(resposta.get("channel"));
            String status = resposta == null ? null : String.valueOf(resposta.get("status"));
            log.info("Verify enviar: solicitado={} twilioChannel={} status={} to={}",
                    canalSolicitado, canalTwilio, status, mascarar(telefoneE164));
        } catch (RestClientException e) {
            log.warn("Verify enviar falhou (canal={}, to={}): {}",
                    canalSolicitado, mascarar(telefoneE164), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar o código de verificação. Tente novamente.");
        }
    }

    /** Mascara o telefone para log/auditoria: mantém só os 4 últimos dígitos. */
    private static String mascarar(String e164) {
        return e164 == null || e164.length() < 4 ? "***" : "***" + e164.substring(e164.length() - 4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean checar(String telefoneE164, String codigo) {
        exigirConfig();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", telefoneE164);
        body.add("Code", codigo == null ? "" : codigo.trim());
        try {
            Map<String, Object> resposta = rest.post().uri("/VerificationCheck")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return resposta != null && "approved".equals(resposta.get("status"));
        } catch (RestClientException e) {
            // 404 = sem verificação pendente (expirou/consumido) — trata como código inválido.
            return false;
        }
    }

    private void exigirConfig() {
        if (!configurado) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verificação por SMS/WhatsApp não configurada no servidor.");
        }
    }
}
