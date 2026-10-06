package com.example.pop.clicksign;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página wrapper da cerimônia da Clicksign (Widget Embedded — a TELA é da Clicksign, dentro de um iframe que o
 * SDK monta). A Clicksign renderiza o documento (PDF) e conduz a autenticação por TOKEN (sms/email/whatsapp,
 * conforme {@code CLICKSIGN_AUTENTICACAO}) + a assinatura. O app abre ESTA página no WebView; no front (coassinatura)
 * ela abre numa aba. Ao concluir, o SDK dispara {@code signed} → emitimos {@code zs-doc-signed} (mesmo evento que
 * ZapSign/Autentique usam) para o app avançar; no front a conclusão é detectada por polling. Público (sem JWT).
 *
 * <p><b>Histórico:</b> antes tentamos a Assinatura Incorporada (noWidget/tokenless, {@code embedded_signature}),
 * em que a tela era NOSSA e só aparecia um link do documento. A pedido, voltamos ao Widget Embedded (tela da
 * Clicksign, que já mostra o PDF) — ele aceita qualquer autenticação EXCETO {@code embedded_signature}.
 */
@RestController
public class ClicksignWidgetController {

    /** CDN do SDK do Widget Embedded da Clicksign (API v3). */
    private static final String SDK = "https://cdn-public-library.clicksign.com/embedded/embedded.min-2.1.0.js";
    private static final String HOST_PADRAO = "https://sandbox.clicksign.com";

    @GetMapping(value = "/assinatura/clicksign/widget", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String widget(@RequestParam("signer") String signer,
            @RequestParam(value = "host", required = false) String hostParam) {
        String s = sanitizarSigner(signer);
        if (s == null) {
            return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"></head>"
                    + "<body><p>Assinatura inválida.</p></body></html>";
        }
        String host = sanitizarHost(hostParam);
        if (host == null) {
            host = HOST_PADRAO;
        }
        return """
            <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <title>Assinatura</title>
            <!-- #container PRECISA de height:100% (não min-height): o iframe do widget usa height:100% e, sem uma
                 altura definida no pai, colapsa (~154px) e o documento aparece em branco. -->
            <style>html,body{height:100%;margin:0;background:#fff}#container{width:100%;height:100%}</style>
            </head><body><div id="container"></div>
            <script src="%SDK%"></script>
            <script>
              (function(){
                function post(m){try{if(window.ReactNativeWebView)window.ReactNativeWebView.postMessage(m);}catch(x){}
                  try{if(window.parent&&window.parent!==window)window.parent.postMessage(m,'*');}catch(x){}}
                try{
                  var w=new Clicksign('%SIGNER%');
                  w.endpoint='%HOST%';
                  // '*' (recomendado pela Clicksign em app/WebView) garante o recebimento dos callbacks.
                  w.origin='*';
                  w.on&&w.on('loaded',function(){post('zs-doc-loaded');});
                  w.on&&w.on('signed',function(){post('zs-doc-signed');});
                  // Sem handler de 'resized': o container tem height:100%, então o widget preenche a tela e rola
                  // internamente (setar a altura do documento inteiro aqui faria a página externa rolar).
                  w.mount('container');
                }catch(err){
                  document.getElementById('container').innerText='Não foi possível carregar a assinatura. Tente novamente.';
                }
              })();
            </script></body></html>
            """
                .replace("%SDK%", SDK)
                .replace("%SIGNER%", s)
                .replace("%HOST%", host);
    }

    /** Aceita só o formato de id do signatário (uuid/hex-hífen) — evita injeção no HTML. */
    private static String sanitizarSigner(String signer) {
        if (signer == null || !signer.matches("^[a-zA-Z0-9-]{8,64}$")) {
            return null;
        }
        return signer;
    }

    /** Aceita só uma origin https simples (https://host[:porta]) — evita injeção de script no HTML. */
    private static String sanitizarHost(String host) {
        if (host == null || !host.matches("^https://[a-zA-Z0-9.-]+(:[0-9]{2,5})?$")) {
            return null;
        }
        return host;
    }
}
