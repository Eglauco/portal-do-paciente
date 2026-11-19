package com.example.pop.sms;

import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envio de SMS de texto livre via <b>Comtele</b> (provedor brasileiro, pré-pago, short code BR) —
 * {@code POST https://api.comtele.com.br/messages/sms/send} com header {@code x-api-key}.
 *
 * <p>Configuração por variável de ambiente: {@code COMTELE_API_KEY} (obrigatória) e
 * {@code COMTELE_ROTA} (id da rota; padrão 17 = "Premium", indicada para mensagem transacional;
 * 16 = "Marketing"). A Comtele cobra 1 crédito a cada 153 caracteres e NÃO translitera sozinha:
 * por isso os acentos são removidos aqui antes do envio (o texto fica no alfabeto GSM-7, em menos
 * partes). Fail-open: nunca lança.
 */
@Service
public class ComteleSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(ComteleSmsService.class);
    static final String BASE_URL = "https://api.comtele.com.br";
    static final int ROTA_PADRAO = 17;

    private final RestClient rest;
    private final boolean configurado;
    private final int rota;

    public ComteleSmsService(
            @Value("${comtele.api-key:}") String apiKey,
            @Value("${comtele.rota:}") String rota) {
        String chave = apiKey == null ? "" : apiKey.trim();
        this.configurado = !chave.isBlank();
        this.rota = lerRota(rota);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory).baseUrl(BASE_URL);
        if (configurado) {
            builder.defaultHeader("x-api-key", chave);
        }
        this.rest = builder.build();
    }

    @Override
    public boolean configurado() {
        return configurado;
    }

    @Override
    public boolean enviar(String telefoneE164, String texto) {
        if (!configurado || telefoneE164 == null || telefoneE164.isBlank() || texto == null || texto.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> resposta = rest.post().uri("/messages/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(corpo(telefoneE164, texto, rota))
                    .retrieve()
                    .body(Map.class);
            boolean aceito = aceito(resposta);
            if (aceito) {
                log.info("SMS enviado via Comtele (to={})", mascarar(telefoneE164));
            } else {
                log.warn("Comtele recusou o SMS (to={}): {}", mascarar(telefoneE164), erro(resposta));
            }
            return aceito;
        } catch (RestClientException e) {
            log.warn("Falha ao enviar SMS via Comtele (to={}): {}", mascarar(telefoneE164), e.getMessage());
            return false;
        }
    }

    /** Corpo do {@code /messages/sms/send}. Package-private para teste. */
    static Map<String, Object> corpo(String telefoneE164, String texto, int rota) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("receivers", List.of(somenteDigitos(telefoneE164)));
        corpo.put("contactGroups", List.of());
        corpo.put("message", semAcentos(texto));
        corpo.put("route", rota);
        corpo.put("tag", "convite-app");
        corpo.put("custom", "");
        corpo.put("scheduleDate", null);
        return corpo;
    }

    /** A Comtele responde {@code hasError=false} quando aceita. Package-private para teste. */
    static boolean aceito(Map<?, ?> resposta) {
        return resposta != null && Boolean.FALSE.equals(resposta.get("hasError"));
    }

    /** Remove acentos/cedilha ("Saúde" → "Saude"), mantendo o resto do texto. */
    static String semAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    static String somenteDigitos(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    static int lerRota(String valor) {
        try {
            return valor == null || valor.isBlank() ? ROTA_PADRAO : Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return ROTA_PADRAO;
        }
    }

    private static Object erro(Map<?, ?> resposta) {
        if (resposta == null) {
            return "sem resposta";
        }
        Object erros = resposta.get("errors");
        return erros != null ? erros : resposta.get("message");
    }

    private static String mascarar(String e164) {
        return e164 == null || e164.length() < 4 ? "***" : "***" + e164.substring(e164.length() - 4);
    }
}
