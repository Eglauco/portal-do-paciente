package com.example.pop.notificacaoadmin;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.auth.Permissoes;
import com.example.pop.common.Pagina;
import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Sino de notificações do back-office (admin logado). Sob /notificacoes/** → papel
 * ADMIN. Tudo é escopado pelo admin (claim "uid") e pela sua unidade ATIVA.
 */
@RestController
@RequestMapping("/notificacoes")
public class NotificacaoAdminController {

    private final NotificacaoAdminService service;
    private final UsuarioRepository usuarioRepository;

    public NotificacaoAdminController(NotificacaoAdminService service, UsuarioRepository usuarioRepository) {
        this.service = service;
        this.usuarioRepository = usuarioRepository;
    }

    /** Lista as notificações do admin na unidade ativa (mais recentes primeiro). */
    @GetMapping
    public Pagina<NotificacaoAdminResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Long uid = uid(jwt);
        return service.listar(uid, unidadeAtiva(uid), page, size);
    }

    /** Total de não lidas (contador do sino) na unidade ativa. */
    @GetMapping("/nao-lidas")
    public ContagemNaoLidas naoLidas(@AuthenticationPrincipal Jwt jwt) {
        Long uid = uid(jwt);
        return new ContagemNaoLidas(service.contarNaoLidas(uid, unidadeAtiva(uid)));
    }

    /** Marca uma notificação como lida (ao clicar nela). */
    @PostMapping("/{id}/lida")
    public void marcarLida(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.marcarLida(id, uid(jwt));
    }

    /** Marca TODAS as notificações do admin na unidade ativa como lidas. */
    @PostMapping("/marcar-todas-lidas")
    public void marcarTodasLidas(@AuthenticationPrincipal Jwt jwt) {
        Long uid = uid(jwt);
        service.marcarTodasLidas(uid, unidadeAtiva(uid));
    }

    /** Id do admin logado (claim "uid" do token). */
    private Long uid(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        if (uid instanceof Number numero) {
            return numero.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
    }

    /**
     * Unidade ATIVA EFETIVA do admin — a mesma que o login/{@code /auth/me} reportam ao front
     * ({@link Permissoes#unidadeAtivaEfetiva}): a atual se ainda acessível, senão a primeira
     * acessível. Usar o {@code usuario.unidade} cru deixaria o sino numa unidade errada/vazia
     * (ex.: usuário sem unidade persistida, ou removida do perfil). Null se não houver nenhuma.
     */
    private Long unidadeAtiva(Long uid) {
        return usuarioRepository.findById(uid)
                .map(Permissoes::unidadeAtivaEfetiva)
                .map(Unidade::getId)
                .orElse(null);
    }

    public record ContagemNaoLidas(long total) {
    }
}
