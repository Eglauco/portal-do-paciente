package com.example.pop.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Escolhe o provedor do SMS de texto livre (convite para baixar o app) pela variável
 * {@code SMS_PROVEDOR}: {@code infobip} ou {@code twilio}. Padrão: Infobip quando configurada, senão
 * Twilio — porque o long code internacional do Twilio não é aceito pelas operadoras brasileiras.
 * O OTP do login NÃO passa por aqui (continua no Twilio Verify).
 */
@Service
@Primary
public class RoteadorSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(RoteadorSmsService.class);

    private final SmsService escolhido;

    public RoteadorSmsService(
            @Value("${sms.provedor:}") String provedor,
            InfobipSmsService infobip,
            TwilioSmsService twilio) {
        this.escolhido = escolher(provedor, infobip, twilio);
        log.info("SMS de texto livre (convite) via {}", escolhido == infobip ? "Infobip" : "Twilio");
    }

    /** Package-private para teste. */
    static SmsService escolher(String provedor, SmsService infobip, SmsService twilio) {
        String p = provedor == null ? "" : provedor.trim().toLowerCase();
        if (p.equals("twilio")) {
            return twilio;
        }
        if (p.equals("infobip")) {
            return infobip;
        }
        return infobip.configurado() ? infobip : twilio;
    }

    @Override
    public boolean enviar(String telefoneE164, String texto) {
        return escolhido.enviar(telefoneE164, texto);
    }

    @Override
    public boolean configurado() {
        return escolhido.configurado();
    }
}
