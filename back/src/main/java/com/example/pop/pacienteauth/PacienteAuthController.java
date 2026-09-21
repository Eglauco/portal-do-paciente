package com.example.pop.pacienteauth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

import com.example.pop.paciente.ContaApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteAcessoService.Perfil;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

/**
 * Login do paciente no app: o OTP autentica a CONTA (telefone) e amarra o
 * aparelho; a tela "Selecionar Perfil" escolhe por qual paciente agir. Trocar de
 * perfil reemite o token com o mesmo aparelho — nunca refaz OTP.
 */
@RestController
@RequestMapping("/paciente-auth")
public class PacienteAuthController {

    private final PacienteAcessoService acessoService;
    private final StorageService storageService;
    private final JwtEncoder jwtEncoder;
    private final long expiracaoDias;

    public PacienteAuthController(PacienteAcessoService acessoService, StorageService storageService,
            JwtEncoder jwtEncoder, @Value("${app.jwt.paciente-expiration-days:365}") long expiracaoDias) {
        this.acessoService = acessoService;
        this.storageService = storageService;
        this.jwtEncoder = jwtEncoder;
        this.expiracaoDias = expiracaoDias;
    }

    /**
     * Início do login: confere a identidade (telefone+CPF+data) e informa se a conta já tem senha
     * (o app mostra o campo de senha) e se está bloqueada por tentativas. NÃO envia SMS.
     */
    @PostMapping("/iniciar")
    public InicioLoginResponse iniciar(@Valid @RequestBody IniciarLoginRequest request) {
        PacienteAcessoService.InicioLogin r =
                acessoService.iniciar(request.cpf(), request.dataNascimento(), request.telefone());
        return new InicioLoginResponse(r.temSenha(), r.bloqueada());
    }

    /** O usuário pede o código (SMS) informando o CPF; devolve o telefone mascarado do envio. */
    @PostMapping("/solicitar-codigo")
    public SolicitarCodigoResponse solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequest request) {
        return new SolicitarCodigoResponse(
                acessoService.solicitarCodigo(request.cpf(), request.dataNascimento(), request.telefone()));
    }

    /** Login por SENHA (sem SMS): identidade + PIN → sessão (mesmo formato do OTP). */
    @PostMapping("/login-senha")
    public AtivarResponse loginSenha(@Valid @RequestBody LoginSenhaRequest request) {
        String dispositivoId = request.dispositivoId().trim();
        ContaApp conta = acessoService.loginPorSenha(request.cpf(), request.dataNascimento(), request.telefone(),
                request.senha(), dispositivoId);
        return montarResposta(conta, dispositivoId, false);
    }

    /** Define a senha (PIN) inicial após o OTP (obrigatório antes de escolher o perfil). */
    @PostMapping("/definir-senha")
    public void definirSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DefinirSenhaRequest request) {
        ContaApp conta = acessoService.contaParaTroca(jwt); // valida a conta (cid) + aparelho
        acessoService.definirSenhaInicial(conta.getId(), jwt.getClaimAsString("dev"), request.senha());
    }

    /**
     * Ativa o app: valida CPF + código, amarra o aparelho à conta e devolve um token
     * para um perfil padrão (o próprio, ou o primeiro dependente) + a lista de perfis
     * para o app abrir a tela "Selecionar Perfil".
     */
    @PostMapping("/ativar")
    public AtivarResponse ativar(@Valid @RequestBody AtivarPacienteRequest request) {
        String dispositivoId = request.dispositivoId().trim();
        ContaApp conta = acessoService.ativar(request.cpf(), request.dataNascimento(), request.telefone(),
                request.codigo(), dispositivoId);
        // Pós-OTP: a senha foi resetada — o app deve pedir um novo PIN antes de escolher o perfil.
        return montarResposta(conta, dispositivoId, true);
    }

    /** Monta a sessão (token do perfil padrão + lista de perfis) comum ao OTP e ao login por senha. */
    private AtivarResponse montarResposta(ContaApp conta, String dispositivoId, boolean precisaDefinirSenha) {
        List<Perfil> perfis = acessoService.perfis(conta.getCpf());
        Perfil padrao = perfis.stream().filter(Perfil::proprio).findFirst()
                .orElse(perfis.isEmpty() ? null : perfis.get(0));
        if (padrao == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nenhum perfil disponível para este CPF");
        }
        String token = gerarToken(conta, padrao.paciente(), dispositivoId);
        return new AtivarResponse(token, padrao.paciente().getId(), padrao.paciente().getNome(),
                perfis.stream().map(this::paraResposta).toList(), precisaDefinirSenha);
    }

    /** Perfis que a sessão atual pode acessar (para a tela "Selecionar Perfil"). */
    @GetMapping("/perfis")
    public List<PerfilResponse> perfis(@AuthenticationPrincipal Jwt jwt) {
        String cpf = acessoService.cpfDaSessao(jwt);
        return acessoService.perfis(cpf).stream().map(this::paraResposta).toList();
    }

    /** Escolhe o perfil (paciente) a agir: valida o vínculo e reemite o token (mesmo aparelho). */
    @PostMapping("/trocar-perfil")
    public PacienteSessaoResponse trocarPerfil(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrocarPerfilRequest request) {
        ContaApp conta = acessoService.contaParaTroca(jwt);
        Paciente perfil = acessoService.perfilDaConta(conta.getCpf(), request.pacienteId());
        String token = gerarToken(conta, perfil, jwt.getClaimAsString("dev"));
        return new PacienteSessaoResponse(token, perfil.getId(), perfil.getNome());
    }

    /** Reidrata a sessão (valida token + aparelho ativo) devolvendo o perfil atual. */
    @GetMapping("/me")
    public PacienteSessaoResponse me(@AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        return new PacienteSessaoResponse(null, paciente.getId(), paciente.getNome());
    }

    private PerfilResponse paraResposta(Perfil perfil) {
        Paciente p = perfil.paciente();
        return new PerfilResponse(p.getId(), p.getNome(),
                storageService.urlFotoPaciente(p.getFotoUrl()), perfil.proprio(), perfil.permissoes());
    }

    /** Token do app: conta (cid) + perfil ativo (pid), amarrado ao aparelho (dev). */
    private String gerarToken(ContaApp conta, Paciente perfil, String dispositivoId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(conta.getCpf())
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofDays(expiracaoDias)))
                .claim("cid", conta.getId())
                .claim("pid", perfil.getId())
                .claim("dev", dispositivoId)
                .claim("role", "PACIENTE")
                .claim("nome", perfil.getNome())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
