package com.example.pop.prontuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.usuario.UsuarioRepository;

/**
 * Coassinatura do PROFISSIONAL de saúde (painel back-office). O profissional logado (usuário vinculado a um
 * ProfissionalSaude) vê seus termos aguardando assinatura e coassina com a cerimônia embutida no front. Sob
 * /coassinatura/** → ADMIN (todos os usuários do back-office têm role ADMIN); a POSSE é conferida no servidor
 * pelo profissionalSaude do usuário logado (não confia no front).
 */
@RestController
@RequestMapping("/coassinatura")
public class CoassinaturaController {

    private final AssinaturaTermoService service;
    private final UsuarioRepository usuarioRepository;

    public CoassinaturaController(AssinaturaTermoService service, UsuarioRepository usuarioRepository) {
        this.service = service;
        this.usuarioRepository = usuarioRepository;
    }

    /** Atendimentos com termos aguardando a coassinatura do profissional logado. */
    @GetMapping("/meus-termos")
    public List<TermoProfissionalResponse> meusTermos(@AuthenticationPrincipal Jwt jwt) {
        return service.listarTermosProfissional(profissionalDoToken(jwt));
    }

    /** Confere no provedor se o documento concluiu (após o profissional assinar) e marca ASSINADO. */
    @PostMapping("/{prontuarioId}/conferir")
    public List<TermoAssinaturaResponse> conferir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long prontuarioId) {
        return service.conferirProfissional(prontuarioId, profissionalDoToken(jwt)).stream()
                .map(TermoAssinaturaResponse::from).toList();
    }

    /** Resolve o profissional vinculado ao usuário logado; 403 se o usuário não for um profissional. */
    private Long profissionalDoToken(Jwt jwt) {
        Long profId = usuarioRepository.findByEmailIgnoreCase(jwt.getSubject())
                .map(u -> u.getProfissionalSaudeId())
                .orElse(null);
        if (profId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seu usuário não está vinculado a um profissional de saúde.");
        }
        return profId;
    }
}
