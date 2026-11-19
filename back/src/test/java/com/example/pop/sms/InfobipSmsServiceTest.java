package com.example.pop.sms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Corpo/parse da Infobip e a escolha do provedor (sem rede). */
class InfobipSmsServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void corpoTiraOMaisTranslitera() {
        Map<String, Object> corpo = InfobipSmsService.corpo("+5518988172191", "Olá", "");
        Map<String, Object> msg = ((List<Map<String, Object>>) corpo.get("messages")).get(0);
        assertThat(msg.get("destinations")).isEqualTo(List.of(Map.of("to", "5518988172191")));
        assertThat(msg.get("text")).isEqualTo("Olá");
        assertThat(msg.get("transliteration")).isEqualTo("PORTUGUESE");
        assertThat(msg).doesNotContainKey("from");
        Map<String, Object> comRemetente = ((List<Map<String, Object>>) InfobipSmsService
                .corpo("+5518988172191", "x", "INTEGRASAUDE").get("messages")).get(0);
        assertThat(comRemetente.get("from")).isEqualTo("INTEGRASAUDE");
    }

    @Test
    void grupoDoStatusLeAPrimeiraMensagem() {
        Map<String, Object> ok = Map.of("messages", List.of(Map.of("status", Map.of("groupName", "PENDING"))));
        Map<String, Object> rejeitado = Map.of("messages", List.of(Map.of("status", Map.of("groupName", "REJECTED"))));
        assertThat(InfobipSmsService.grupoDoStatus(ok)).isEqualTo("PENDING");
        assertThat(InfobipSmsService.grupoDoStatus(rejeitado)).isEqualTo("REJECTED");
        assertThat(InfobipSmsService.grupoDoStatus(Map.of())).isNull();
        assertThat(InfobipSmsService.grupoDoStatus(null)).isNull();
    }

    @Test
    void normalizaBaseUrl() {
        assertThat(InfobipSmsService.normalizarBaseUrl("abc.api.infobip.com")).isEqualTo("https://abc.api.infobip.com");
        assertThat(InfobipSmsService.normalizarBaseUrl("https://abc.api.infobip.com/")).isEqualTo("https://abc.api.infobip.com");
        assertThat(InfobipSmsService.normalizarBaseUrl("  ")).isEmpty();
    }

    @Test
    void semConfiguracaoNaoEnvia() {
        InfobipSmsService s = new InfobipSmsService("", "", "");
        assertThat(s.configurado()).isFalse();
        assertThat(s.enviar("+5518988172191", "oi")).isFalse();
    }

    @Test
    void roteadorEscolheProvedor() {
        SmsService infobipOk = new InfobipSmsService("abc.api.infobip.com", "chave", "");
        SmsService infobipVazio = new InfobipSmsService("", "", "");
        SmsService twilio = new TwilioSmsService("", "", "", "");
        assertThat(RoteadorSmsService.escolher("", infobipOk, twilio)).isSameAs(infobipOk);
        assertThat(RoteadorSmsService.escolher("", infobipVazio, twilio)).isSameAs(twilio);
        assertThat(RoteadorSmsService.escolher("twilio", infobipOk, twilio)).isSameAs(twilio);
        assertThat(RoteadorSmsService.escolher("INFOBIP", infobipVazio, twilio)).isSameAs(infobipVazio);
    }
}
