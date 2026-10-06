package com.example.pop.prontuario;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.PacienteAcessoService;

/**
 * Assinatura de termos (TCLE) pelo paciente logado (app). Sob /meu/** → papel PACIENTE; o termo tem
 * de ser de um prontuário do próprio paciente (posse conferida no serviço).
 */
@RestController
@RequestMapping("/meu/termos")
public class MeusTermosController {

    private final AssinaturaTermoService service;
    private final PacienteAcessoService acessoService;

    public MeusTermosController(AssinaturaTermoService service, PacienteAcessoService acessoService) {
        this.service = service;
        this.acessoService = acessoService;
    }

    /** Inicia a assinatura de um termo pendente e devolve o(s) link(s) para abrir a cerimônia no app. */
    @PostMapping("/{id}/assinar")
    public IniciarAssinaturaResponse assinar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.PRONTUARIO);
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return IniciarAssinaturaResponse.de(service.iniciar(id, pacienteId));
    }

    /**
     * Inicia a assinatura de TODOS os termos pendentes de um atendimento (prontuário) numa cerimônia
     * só (lote): 1 termo = documento único; vários = principal + extras, mesmo sign_url.
     */
    @PostMapping("/prontuario/{prontuarioId}/assinar")
    public IniciarAssinaturaResponse assinarLote(@AuthenticationPrincipal Jwt jwt, @PathVariable Long prontuarioId) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.PRONTUARIO);
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return IniciarAssinaturaResponse.de(service.iniciarLote(prontuarioId, pacienteId));
    }

    /**
     * Marca os termos do atendimento como "Assinatura em confirmação" — chamado pelo app assim que a
     * cerimônia conclui (feedback imediato). Devolve os termos atualizados.
     */
    @PostMapping("/prontuario/{prontuarioId}/em-confirmacao")
    public List<TermoAssinaturaResponse> emConfirmacao(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long prontuarioId) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.PRONTUARIO);
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return service.marcarEmConfirmacao(prontuarioId, pacienteId).stream().map(TermoAssinaturaResponse::from).toList();
    }

    /** Conferência do loop: verifica no provedor se já assinou e devolve os termos com o status atual. */
    @GetMapping("/prontuario/{prontuarioId}")
    public List<TermoAssinaturaResponse> conferir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long prontuarioId) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.PRONTUARIO);
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return service.conferir(prontuarioId, pacienteId).stream().map(TermoAssinaturaResponse::from).toList();
    }

    /** Cancela a confirmação (voltar termos EM_CONFIRMACAO para pendente) — quando o paciente não assinou. */
    @PostMapping("/prontuario/{prontuarioId}/cancelar")
    public List<TermoAssinaturaResponse> cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long prontuarioId) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.PRONTUARIO);
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return service.cancelarConfirmacao(prontuarioId, pacienteId).stream().map(TermoAssinaturaResponse::from).toList();
    }

    /**
     * Link(s) da cerimônia + provedor. {@code signUrl} = o primeiro (compat com o app atual); {@code signUrls}
     * = todos (a Autentique no modo "separado" devolve um por termo); {@code provedor} = ZAPSIGN/AUTENTIQUE
     * (o app decide como detectar o fim da cerimônia — a página da Autentique não emite os eventos zs-*).
     */
    public record IniciarAssinaturaResponse(String signUrl, List<String> signUrls, String provedor) {
        static IniciarAssinaturaResponse de(AssinaturaTermoService.InicioAssinatura inicio) {
            List<String> urls = inicio.signUrls();
            return new IniciarAssinaturaResponse(urls.isEmpty() ? null : urls.get(0), urls, inicio.provedor());
        }
    }
}
