package com.example.pop.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final UnidadeRepository unidadeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long expiracaoHoras;
    /** Hash de referência (mesmo custo dos reais) para igualar o tempo quando a conta não existe. */
    private final String hashFicticio;

    public AuthController(UsuarioRepository usuarioRepository, UnidadeRepository unidadeRepository,
            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${app.jwt.expiration-hours:8}") long expiracaoHoras) {
        this.usuarioRepository = usuarioRepository;
        this.unidadeRepository = unidadeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.expiracaoHoras = expiracaoHoras;
        this.hashFicticio = passwordEncoder.encode("timing-guard-nao-usar");
    }

    /** Autentica por e-mail + senha e devolve um JWT assinado. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        boolean temHash = usuario != null && usuario.getSenhaHash() != null;
        // Sempre compara (contra um hash fictício quando a conta não existe) para não
        // vazar, pelo tempo de resposta, se o e-mail existe (evita enumerar contas).
        boolean senhaConfere = passwordEncoder.matches(request.senha(), temHash ? usuario.getSenhaHash() : hashFicticio);
        if (!temHash || !senhaConfere) {
            // Mesma mensagem para usuário inexistente e senha errada.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }

        Instant agora = Instant.now();
        Instant expira = agora.plus(Duration.ofHours(expiracaoHoras));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getEmail())
                .issuedAt(agora)
                .expiresAt(expira)
                .claim("nome", usuario.getNome())
                .claim("uid", usuario.getId())
                .claim("role", "ADMIN")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        Unidade ativa = Permissoes.unidadeAtivaEfetiva(usuario);
        Long unidadeId = ativa == null ? null : ativa.getId();
        String unidadeNome = ativa == null ? null : ativa.getNome();
        return new LoginResponse(token, usuario.getNome(), usuario.getEmail(), unidadeId, unidadeNome, expira,
                Permissoes.telas(usuario), Permissoes.unidades(usuario));
    }

    /** Dados do usuário autenticado (recarrega do banco para refletir a unidade atual). */
    @GetMapping("/me")
    public UsuarioLogadoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UsuarioLogadoResponse.from(usuarioLogado(jwt));
    }

    /** Troca a unidade de saúde ativa do usuário logado (persistida no banco). */
    @PutMapping("/unidade")
    public UsuarioLogadoResponse trocarUnidade(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrocarUnidadeRequest request) {
        Usuario usuario = usuarioLogado(jwt);
        Unidade unidade = unidadeRepository.findById(request.unidadeSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade de saúde não encontrada"));
        // Só permite ativar uma unidade que os perfis do usuário liberam.
        if (!Permissoes.podeAcessarUnidade(usuario, unidade.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unidade fora do seu perfil de acesso");
        }
        usuario.setUnidade(unidade);
        return UsuarioLogadoResponse.from(usuarioRepository.save(usuario));
    }

    private Usuario usuarioLogado(Jwt jwt) {
        return usuarioRepository.findByEmailIgnoreCase(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));
    }
}
