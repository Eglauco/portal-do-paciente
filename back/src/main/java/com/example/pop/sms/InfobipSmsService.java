package com.example.pop.sms;

import java.time.Duration;
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
 * Envio de SMS de texto livre via <b>Infobip</b> ({@code POST /sms/2/text/advanced}). Criado porque as
 * operadoras brasileiras NÃO aceitam long code internacional (o número +1 do Twilio): pelo Twilio o
 * convite só chegava quando a própria Twilio trocava o remetente por um short code BR. A Infobip
 * roteia por short code brasileiro (ex.: 29090) e entrega em todas as operadoras.
 *
 * <p>Configuração por variável de ambiente (igual ao Twilio): {@code INFOBIP_BASE_URL} (ex.:
 * {@code xxxxx.api.infobip.com}, específico da conta) e {@code INFOBIP_API_KEY}; {@code INFOBIP_REMETENTE}
 * é opcional (vazio = a Infobip escolhe o short code). Envia com {@code transliteration=PORTUGUESE}: os
 * acentos viram letras simples, então o texto cabe no alfabeto GSM-7 (160 caracteres por parte, em vez
 * de 70 em Unicode) — mais barato e entregue também em operadoras sem Unicode (Oi/Algar).
 * Fail-open: nunca lança.
 */
@Service
public class InfobipSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(InfobipSmsService.class);

    private final RestClient rest;
    private final boolean configurado;
    private final String remetente;

    public InfobipSmsService(
            @Value("${infobip.base-url:}") String baseUrl,
            @Value("${infobip.api-key:}") String apiKey,
            @Value("${infobip.remetente:}") String remetente) {
        String base = normalizarBaseUrl(baseUrl);
        String chave = apiKey == null ? "" : apiKey.trim();
        this.configurado = !base.isBlank() && !chave.isBlank();
        this.remetente = remetente == null ? "" : remetente.trim();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (configurado) {
            builder.baseUrl(base).defaultHeader("Authorization", "App " + chave);
        }
        this.rest = builder.build();
    }

    @Override
    public boolean configurado() {
        return configurado;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean enviar(String telefoneE164, String texto) {
        if (!configurado || telefoneE164 == null || telefoneE164.isBlank() || texto == null || texto.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> resposta = rest.post().uri("/sms/2/text/advanced")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(corpo(telefoneE164, texto, remetente))
                    .retrieve()
                    .body(Map.class);
            String grupo = grupoDoStatus(resposta);
            boolean aceito = grupo != null && !"REJECTED".equals(grupo);
            if (aceito) {
                log.info("SMS enviado via Infobip (status={}, to={})", grupo, mascarar(telefoneE164));
            } else {
                log.warn("Infobip recusou o SMS (status={}, to={})", grupo, mascarar(telefoneE164));
            }
            return aceito;
        } catch (RestClientException e) {
            log.warn("Falha ao enviar SMS via Infobip (to={}): {}", mascarar(telefoneE164), e.getMessage());
            return false;
        }
    }

    /** Corpo do {@code /sms/2/text/advanced}. Package-private para teste. */
    static Map<String, Object> corpo(String telefoneE164, String texto, String remetente) {
        Map<String, Object> mensagem = new java.util.LinkedHashMap<>();
        mensagem.put("destinations", List.of(Map.of("to", somenteDigitos(telefoneE164))));
        if (remetente != null && !remetente.isBlank()) {
            mensagem.put("from", remetente);
        }
        mensagem.put("text", texto);
        mensagem.put("transliteration", "PORTUGUESE");
        return Map.of("messages", List.of(mensagem));
    }

    /** {@code groupName} do status da 1ª mensagem da resposta (PENDING/REJECTED/...), ou null. Package-private para teste. */
    @SuppressWarnings("unchecked")
    static String grupoDoStatus(Map<String, Object> resposta) {
        if (resposta == null || !(resposta.get("messages") instanceof List<?> lista) || lista.isEmpty()
                || !(lista.get(0) instanceof Map<?, ?> msg) || !(msg.get("status") instanceof Map<?, ?> status)) {
            return null;
        }
        Object grupo = status.get("groupName");
        return grupo == null ? null : grupo.toString();
    }

    /** A Infobip quer o número internacional só com dígitos (sem o {@code +}). */
    static String somenteDigitos(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    /** Aceita "xxxxx.api.infobip.com" ou com https://, sem barra no fim. */
    static String normalizarBaseUrl(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isBlank()) {
            return "";
        }
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "https://" + v;
        }
        return v.replaceAll("/+$", "");
    }

    private static String mascarar(String e164) {
        return e164 == null || e164.length() < 4 ? "***" : "***" + e164.substring(e164.length() - 4);
    }
}
