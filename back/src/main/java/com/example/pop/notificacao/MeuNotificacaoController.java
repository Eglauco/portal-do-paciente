package com.example.pop.notificacao;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.PacienteAcessoService;

/** Notificações do paciente logado (app). Sob /meu/** → papel PACIENTE. */
@RestController
@RequestMapping("/meu/notificacoes")
public class MeuNotificacaoController {

    private final NotificacaoService service;
    private final PacienteAcessoService acessoService;

    public MeuNotificacaoController(NotificacaoService service, PacienteAcessoService acessoService) {
        this.service = service;
        this.acessoService = acessoService;
    }

    /** Lista as notificações do paciente (mais recentes primeiro). */
    @GetMapping
    public Pagina<NotificacaoResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return service.listar(pacienteId, page, size);
    }

    /** Total de notificações ainda não lidas (contador do sino). */
    @GetMapping("/nao-lidas")
    public ContagemNaoLidas naoLidas(@AuthenticationPrincipal Jwt jwt) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return new ContagemNaoLidas(service.contarNaoLidas(pacienteId));
    }

    /** Marca a notificação como lida (ao tocar nela no app). */
    @PostMapping("/{id}/lida")
    public void marcarLida(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        service.marcarLida(id, pacienteId);
    }

    public record ContagemNaoLidas(long total) {
    }
}
