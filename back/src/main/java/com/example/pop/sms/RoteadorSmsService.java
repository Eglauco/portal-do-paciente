package com.example.pop.sms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * SMS de texto livre (convite para baixar o app) com <b>provedores em cascata</b>: tenta o 1º
 * configurado e, se ele recusar/falhar, o próximo — assim uma suspensão de conta num provedor não
 * para os convites.
 *
 * <p>Ordem pela variável {@code SMS_PROVEDOR}, lista separada por vírgula (ex.: {@code comtele,infobip,twilio}).
 * Vazio = {@code comtele,infobip,twilio}. Provedor sem credencial é pulado. Um nome sozinho (ex.:
 * {@code infobip}) vira o 1º e os demais continuam como reserva. O OTP do login NÃO passa por aqui
 * (continua no Twilio Verify).
 */
@Service
@Primary
public class RoteadorSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(RoteadorSmsService.class);
    static final List<String> ORDEM_PADRAO = List.of("comtele", "infobip", "twilio");

    private final List<Map.Entry<String, SmsService>> cadeia;

    public RoteadorSmsService(
            @Value("${sms.provedor:}") String provedor,
            ComteleSmsService comtele,
            InfobipSmsService infobip,
            TwilioSmsService twilio) {
        Map<String, SmsService> todos = new LinkedHashMap<>();
        todos.put("comtele", comtele);
        todos.put("infobip", infobip);
        todos.put("twilio", twilio);
        this.cadeia = montarCadeia(provedor, todos);
        log.info("SMS de texto livre (convite): ordem {}", cadeia.stream().map(Map.Entry::getKey).toList());
    }

    /** Provedores configurados na ordem pedida (os não citados entram depois, na ordem padrão). Package-private p/ teste. */
    static List<Map.Entry<String, SmsService>> montarCadeia(String provedor, Map<String, SmsService> todos) {
        List<String> ordem = new ArrayList<>();
        if (provedor != null) {
            for (String p : provedor.toLowerCase().split(",")) {
                String nome = p.trim();
                if (todos.containsKey(nome) && !ordem.contains(nome)) {
                    ordem.add(nome);
                }
            }
        }
        for (String nome : ORDEM_PADRAO) {
            if (!ordem.contains(nome)) {
                ordem.add(nome);
            }
        }
        List<Map.Entry<String, SmsService>> cadeia = new ArrayList<>();
        for (String nome : ordem) {
            SmsService s = todos.get(nome);
            if (s != null && s.configurado()) {
                cadeia.add(Map.entry(nome, s));
            }
        }
        return cadeia;
    }

    @Override
    public boolean enviar(String telefoneE164, String texto) {
        for (int i = 0; i < cadeia.size(); i++) {
            Map.Entry<String, SmsService> p = cadeia.get(i);
            if (p.getValue().enviar(telefoneE164, texto)) {
                return true;
            }
            if (i + 1 < cadeia.size()) {
                log.warn("SMS não saiu por {}; tentando {}", p.getKey(), cadeia.get(i + 1).getKey());
            }
        }
        return false;
    }

    @Override
    public boolean configurado() {
        return !cadeia.isEmpty();
    }
}
