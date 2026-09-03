package com.example.pop.lembrete;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.paciente.PacienteAcessoService;

/** Lembretes do lado do paciente (app): pop-ups pendentes e reconhecimento. */
@RestController
@RequestMapping("/meu/lembretes")
public class MeuLembreteController {

    private final LembreteService lembreteService;
    private final PacienteAcessoService acessoService;

    public MeuLembreteController(LembreteService lembreteService, PacienteAcessoService acessoService) {
        this.lembreteService = lembreteService;
        this.acessoService = acessoService;
    }

    /** Pop-ups de lembrete a mostrar ao abrir o app (reaparecem até reconhecer). */
    @GetMapping("/popups")
    public List<LembretePopupResponse> popups(@AuthenticationPrincipal Jwt jwt) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return lembreteService.popupsPendentes(pacienteId);
    }

    /** Reconhece o pop-up (não reaparece mais). */
    @PostMapping("/{notificacaoId}/reconhecer")
    public void reconhecer(@AuthenticationPrincipal Jwt jwt, @PathVariable Long notificacaoId) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        lembreteService.reconhecer(notificacaoId, pacienteId);
    }
}
