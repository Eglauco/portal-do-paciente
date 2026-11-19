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

    /** Provedor falso: configurado? e resultado do envio fixos; conta as chamadas. */
    static final class Falso implements SmsService {
        final boolean configurado;
        final boolean resultado;
        int chamadas;

        Falso(boolean configurado, boolean resultado) {
            this.configurado = configurado;
            this.resultado = resultado;
        }

        @Override
        public boolean enviar(String telefoneE164, String texto) {
            chamadas++;
            return resultado;
        }

        @Override
        public boolean configurado() {
            return configurado;
        }
    }

    private static List<String> nomes(String provedor, Map<String, SmsService> todos) {
        return RoteadorSmsService.montarCadeia(provedor, todos).stream().map(Map.Entry::getKey).toList();
    }

    @Test
    void cadeiaRespeitaOrdemEPulaNaoConfigurado() {
        Map<String, SmsService> todos = new java.util.LinkedHashMap<>();
        todos.put("comtele", new Falso(true, true));
        todos.put("infobip", new Falso(true, true));
        todos.put("twilio", new Falso(false, true));
        assertThat(nomes("", todos)).containsExactly("comtele", "infobip");
        assertThat(nomes(null, todos)).containsExactly("comtele", "infobip");
        assertThat(nomes("INFOBIP", todos)).containsExactly("infobip", "comtele");
        assertThat(nomes("twilio, infobip ,xyz", todos)).containsExactly("infobip", "comtele");
    }

    @Test
    void cascataTentaOProximoQuandoOPrimeiroFalha() {
        Falso comtele = new Falso(true, false);
        Falso infobip = new Falso(true, false);
        Falso twilio = new Falso(true, true);
        Map<String, SmsService> todos = new java.util.LinkedHashMap<>();
        todos.put("comtele", comtele);
        todos.put("infobip", infobip);
        todos.put("twilio", twilio);
        List<Map.Entry<String, SmsService>> cadeia = RoteadorSmsService.montarCadeia("", todos);
        boolean ok = false;
        for (Map.Entry<String, SmsService> p : cadeia) {
            if (p.getValue().enviar("+5511999999999", "x")) {
                ok = true;
                break;
            }
        }
        assertThat(ok).isTrue();
        assertThat(comtele.chamadas).isEqualTo(1);
        assertThat(infobip.chamadas).isEqualTo(1);
        assertThat(twilio.chamadas).isEqualTo(1);
    }

    @Test
    void roteadorRealUsaComteleAntesESemNadaNaoEnvia() {
        ComteleSmsService comteleVazio = new ComteleSmsService("", "");
        InfobipSmsService infobipVazio = new InfobipSmsService("", "", "");
        TwilioSmsService twilioVazio = new TwilioSmsService("", "", "", "");
        RoteadorSmsService r = new RoteadorSmsService("", comteleVazio, infobipVazio, twilioVazio);
        assertThat(r.configurado()).isFalse();
        assertThat(r.enviar("+5511999999999", "oi")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void comteleCorpoTiraAcentoEUsaRotaPremium() {
        Map<String, Object> corpo = ComteleSmsService.corpo("+5518988172191", "Integra Saúde: ação", ComteleSmsService.lerRota(""));
        assertThat((List<String>) corpo.get("receivers")).containsExactly("5518988172191");
        assertThat(corpo.get("message")).isEqualTo("Integra Saude: acao");
        assertThat(corpo.get("route")).isEqualTo(17);
        assertThat(corpo).containsKey("scheduleDate");
        assertThat(ComteleSmsService.lerRota("16")).isEqualTo(16);
        assertThat(ComteleSmsService.lerRota("abc")).isEqualTo(17);
        assertThat(ComteleSmsService.aceito(Map.of("hasError", false))).isTrue();
        assertThat(ComteleSmsService.aceito(Map.of("hasError", true))).isFalse();
        assertThat(ComteleSmsService.aceito(null)).isFalse();
        assertThat(new ComteleSmsService(" ", "").configurado()).isFalse();
    }
}
