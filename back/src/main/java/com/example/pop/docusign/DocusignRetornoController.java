package com.example.pop.docusign;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página de RETORNO da cerimônia embutida do DocuSign. Ao concluir (ou cancelar) a assinatura no WebView, o
 * DocuSign redireciona o navegador para esta URL com {@code ?event=signing_complete} (entre outros). Quando a
 * assinatura foi concluída, a página emite {@code zs-doc-signed} para o app avançar automaticamente (mesmo
 * evento que ZapSign/Clicksign usam); nos demais casos apenas informa que pode voltar ao app (há também o
 * botão manual "Assinei"). A confirmação REAL vem depois pela conferência ativa/webhook. Público (sem JWT).
 */
@RestController
public class DocusignRetornoController {

    @GetMapping(value = "/assinatura/docusign/retorno", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String retorno(@RequestParam(value = "event", required = false) String event) {
        boolean concluido = event != null && event.equalsIgnoreCase("signing_complete");
        String msg = concluido ? "Assinatura concluída. Você pode voltar ao aplicativo."
                : "Você saiu da assinatura. Volte ao aplicativo para tentar novamente.";
        String emitir = concluido
                ? "try{if(window.ReactNativeWebView)window.ReactNativeWebView.postMessage('zs-doc-signed');}catch(e){}"
                        + "try{if(window.parent&&window.parent!==window)window.parent.postMessage('zs-doc-signed','*');}catch(e){}"
                : "";
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Assinatura</title>"
                + "<style>html,body{height:100%;margin:0;background:#fff;font-family:-apple-system,Roboto,Arial,sans-serif}"
                + ".c{display:flex;height:100%;align-items:center;justify-content:center;padding:24px;text-align:center;color:#243}"
                + "</style></head><body><div class=\"c\"><p>" + msg + "</p></div>"
                + "<script>" + emitir + "</script></body></html>";
    }
}
