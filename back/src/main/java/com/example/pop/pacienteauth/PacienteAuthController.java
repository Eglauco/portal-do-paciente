package com.example.pop.pacienteauth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;

import jakarta.validation.Valid;

/** Login do paciente no app: ativação por telefone + código e sessão amarrada ao aparelho. */
@RestController
@RequestMapping("/paciente-auth")
public class PacienteAuthController {

    private final PacienteAcessoService acessoService;
    private final JwtEncoder jwtEncoder;
    private final long expiracaoDias;

    public PacienteAuthController(PacienteAcessoService acessoService, JwtEncoder jwtEncoder,
            @Value("${app.jwt.paciente-expiration-days:365}") long expiracaoDias) {
        this.acessoService = acessoService;
        this.jwtEncoder = jwtEncoder;
        this.expiracaoDias = expiracaoDias;
    }

    /** Ativa o app: valida telefone + código, amarra o aparelho e devolve um token de longa duração. */
    @PostMapping("/ativar")
    public PacienteSessaoResponse ativar(@Valid @RequestBody AtivarPacienteRequest request) {
        String dispositivoId = request.dispositivoId().trim();
        Paciente paciente = acessoService.ativar(request.telefone(), request.codigo(), dispositivoId);
        return new PacienteSessaoResponse(gerarToken(paciente, dispositivoId), paciente.getId(), paciente.getNome());
    }

    /** Reidrata a sessão (valida token + aparelho ativo). */
    @GetMapping("/me")
    public PacienteSessaoResponse me(@AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = pacienteDoToken(jwt);
        return new PacienteSessaoResponse(null, paciente.getId(), paciente.getNome());
    }

    private String gerarToken(Paciente paciente, String dispositivoId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(paciente.getTelefone())
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofDays(expiracaoDias)))
                .claim("pid", paciente.getId())
                .claim("dev", dispositivoId)
                .claim("role", "PACIENTE")
                .claim("nome", paciente.getNome())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Paciente pacienteDoToken(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number numero)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return acessoService.validarSessao(numero.longValue(), jwt.getClaimAsString("dev"));
    }
}
