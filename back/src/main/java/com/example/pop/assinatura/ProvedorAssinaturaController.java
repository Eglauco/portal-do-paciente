package com.example.pop.assinatura;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/**
 * Tela "Provedores de assinatura": o cliente administra as credenciais (token + webhook secret) e o ambiente de
 * cada provedor, escolhe o provedor ATIVO e testa a conexão — sem depender de deploy/suporte. Os segredos ficam
 * CIFRADOS no banco e são WRITE-ONLY (a API nunca devolve o valor em claro, só um flag "preenchido"). Fase 1:
 * ZapSign, Autentique e Clicksign (DocuSign segue via ambiente). Sob /provedores-assinatura (ADMIN; o acesso
 * fino é pela Tela PROVEDORES_ASSINATURA no front).
 */
@RestController
@RequestMapping("/provedores-assinatura")
public class ProvedorAssinaturaController {

    private static final List<ProvedorAssinatura> GERIDOS =
            List.of(ProvedorAssinatura.ZAPSIGN, ProvedorAssinatura.AUTENTIQUE, ProvedorAssinatura.CLICKSIGN);

    private final ConfiguracaoService config;
    private final AssinaturaProviderFactory factory;

    public ProvedorAssinaturaController(ConfiguracaoService config, AssinaturaProviderFactory factory) {
        this.config = config;
        this.factory = factory;
    }

    /** Chaves de config + rótulo de um provedor gerido pela tela. */
    private record Chaves(String token, String webhook, String ambiente, String urlSandbox, String urlProducao,
            String nome) {
    }

    private static Chaves chaves(ProvedorAssinatura p) {
        return switch (p) {
            case ZAPSIGN -> new Chaves(ChaveConfiguracao.ZAPSIGN_API_TOKEN, ChaveConfiguracao.ZAPSIGN_WEBHOOK_SECRET,
                    ChaveConfiguracao.ZAPSIGN_AMBIENTE, ChaveConfiguracao.ZAPSIGN_URL_SANDBOX,
                    ChaveConfiguracao.ZAPSIGN_URL_PRODUCAO, "ZapSign");
            case AUTENTIQUE -> new Chaves(ChaveConfiguracao.AUTENTIQUE_API_TOKEN,
                    ChaveConfiguracao.AUTENTIQUE_WEBHOOK_SECRET, ChaveConfiguracao.AUTENTIQUE_AMBIENTE,
                    ChaveConfiguracao.AUTENTIQUE_URL_SANDBOX, ChaveConfiguracao.AUTENTIQUE_URL_PRODUCAO, "Autentique");
            case CLICKSIGN -> new Chaves(ChaveConfiguracao.CLICKSIGN_ACCESS_TOKEN,
                    ChaveConfiguracao.CLICKSIGN_WEBHOOK_SECRET, ChaveConfiguracao.CLICKSIGN_AMBIENTE,
                    ChaveConfiguracao.CLICKSIGN_URL_SANDBOX, ChaveConfiguracao.CLICKSIGN_URL_PRODUCAO, "Clicksign");
            default -> null; // DocuSign: fase 2 (credenciais ainda via ambiente)
        };
    }

    /** Status de um provedor gerido (SEM os valores dos segredos — só se estão preenchidos; URLs vêm em claro). */
    public record ProvedorStatus(String id, String nome, String ambiente, String urlSandbox, String urlProducao,
            boolean tokenPreenchido, boolean webhookPreenchido, boolean disponivel) {
    }

    public record ProvedoresResponse(String ativo, List<ProvedorStatus> provedores) {
    }

    @GetMapping
    public ProvedoresResponse listar() {
        List<ProvedorStatus> out = new ArrayList<>();
        for (ProvedorAssinatura p : GERIDOS) {
            Chaves k = chaves(p);
            out.add(new ProvedorStatus(p.name(), k.nome(), ambienteAtual(k.ambiente()),
                    textoOuVazio(k.urlSandbox()), textoOuVazio(k.urlProducao()),
                    config.segredoPreenchido(k.token()), config.segredoPreenchido(k.webhook()),
                    factory.porId(p).disponivel()));
        }
        return new ProvedoresResponse(ativoAtual(), out);
    }

    /** token/webhookSecret em branco MANTÉM o valor atual (não precisa redigitar o segredo para mudar outra coisa). */
    public record CredenciaisRequest(String token, String webhookSecret, String ambiente,
            String urlSandbox, String urlProducao) {
    }

    @PutMapping("/{id}")
    public ProvedoresResponse salvar(@PathVariable String id, @RequestBody CredenciaisRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Chaves k = chaves(provedorGerido(id));
        Long uid = uid(jwt);
        config.salvarSegredo(k.token(), req.token(), uid);
        config.salvarSegredo(k.webhook(), req.webhookSecret(), uid);
        config.salvarTexto(k.ambiente(), normalizarAmbiente(req.ambiente()), uid);
        if (req.urlSandbox() != null && !req.urlSandbox().isBlank()) {
            config.salvarTexto(k.urlSandbox(), req.urlSandbox().trim(), uid);
        }
        if (req.urlProducao() != null && !req.urlProducao().isBlank()) {
            config.salvarTexto(k.urlProducao(), req.urlProducao().trim(), uid);
        }
        return listar();
    }

    @PostMapping("/{id}/testar")
    public AssinaturaProvider.ResultadoTeste testar(@PathVariable String id) {
        return factory.porId(provedorGerido(id)).testarConexao();
    }

    public record AtivoRequest(String provedor) {
    }

    @PutMapping("/ativo")
    public ProvedoresResponse definirAtivo(@RequestBody AtivoRequest req, @AuthenticationPrincipal Jwt jwt) {
        ProvedorAssinatura p = provedor(req.provedor()); // aceita qualquer provedor válido (inclui DOCUSIGN)
        config.salvarTexto(ChaveConfiguracao.PROVEDOR_ASSINATURA, p.name(), uid(jwt));
        return listar();
    }

    // ---- helpers ----

    /** Provedor pelo id, EXIGINDO que seja um dos geridos pela tela (senão 404). */
    private ProvedorAssinatura provedorGerido(String id) {
        ProvedorAssinatura p = provedor(id);
        if (chaves(p) == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O provedor " + p.name() + " ainda não é gerido por esta tela.");
        }
        return p;
    }

    private ProvedorAssinatura provedor(String id) {
        try {
            return ProvedorAssinatura.valueOf(id == null ? "" : id.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provedor desconhecido: " + id);
        }
    }

    private String ambienteAtual(String chave) {
        String v = config.lerTexto(chave);
        return v == null || v.isBlank() ? "SANDBOX" : v.trim().toUpperCase();
    }

    private String textoOuVazio(String chave) {
        String v = config.lerTexto(chave);
        return v == null ? "" : v.trim();
    }

    private static String normalizarAmbiente(String amb) {
        return "PRODUCAO".equalsIgnoreCase(amb == null ? "" : amb.trim()) ? "PRODUCAO" : "SANDBOX";
    }

    private String ativoAtual() {
        String v = config.lerTexto(ChaveConfiguracao.PROVEDOR_ASSINATURA);
        return v == null || v.isBlank() ? ProvedorAssinatura.ZAPSIGN.name() : v.trim().toUpperCase();
    }

    private Long uid(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        if (uid instanceof Number n) {
            return n.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
    }
}
