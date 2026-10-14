package com.example.pop.pacienteauth;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

import com.example.pop.inquilino.InquilinoService;
import com.example.pop.inquilino.PacienteLogin;
import com.example.pop.inquilino.PacienteLoginRepository;
import com.example.pop.inquilino.RoteamentoPacienteService;
import com.example.pop.paciente.ContaApp;
import com.example.pop.paciente.Documentos;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteAcessoService.Perfil;
import com.example.pop.storage.StorageService;
import com.example.pop.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
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
    private final InquilinoService inquilinoService;
    private final PacienteLoginRepository pacienteLoginRepository;
    private final RoteamentoPacienteService roteamentoService;
    private final long expiracaoDias;

    public PacienteAuthController(PacienteAcessoService acessoService, StorageService storageService,
            JwtEncoder jwtEncoder, InquilinoService inquilinoService,
            PacienteLoginRepository pacienteLoginRepository, RoteamentoPacienteService roteamentoService,
            @Value("${app.jwt.paciente-expiration-days:365}") long expiracaoDias) {
        this.acessoService = acessoService;
        this.storageService = storageService;
        this.jwtEncoder = jwtEncoder;
        this.inquilinoService = inquilinoService;
        this.pacienteLoginRepository = pacienteLoginRepository;
        this.roteamentoService = roteamentoService;
        this.expiracaoDias = expiracaoDias;
    }

    /** Mensagem GENÉRICA de identidade (não revela qual campo nem se o CPF existe) — espelha a do serviço. */
    private static final String MSG_IDENTIDADE =
            "Telefone, CPF ou data de nascimento não confere. Verifique os dados ou procure a sua unidade de saúde.";

    /**
     * Resolve o inquilino do login do app. Fase B: o app A-baked envia o inquilino no header
     * {@code X-Inquilino} (o {@code SubdominioTenantFilter} já fixou o schema ANTES deste controller) —
     * esse é o caminho normal. Fallback legado (sem o header): o 1º vínculo do CPF em {@link PacienteLogin}
     * (schema {@code public}), senão o inquilino PADRÃO. O schema é fixado na thread ANTES de consultar o
     * cadastro e restaurado no {@code finally} (não vaza entre requests do pool).
     */
    private Long resolverInquilino(String cpfBruto) {
        String schemaAtual = TenantContext.atualBruto();
        if (schemaAtual != null) {
            Long id = inquilinoService.idPorSchema(schemaAtual);
            if (id != null) {
                return id;
            }
        }
        // Legado (sem X-Inquilino): com 1→N o CPF não resolve mais UM inquilino de forma única — pega o
        // 1º vínculo, senão o padrão. O app baked é a fonte da verdade do inquilino.
        String cpf = Documentos.somenteDigitos(cpfBruto);
        if (cpf == null) {
            return inquilinoService.idPadrao();
        }
        return pacienteLoginRepository.findFirstByCpfOrderByIdAsc(cpf)
                .map(PacienteLogin::getInquilinoId)
                .orElseGet(inquilinoService::idPadrao);
    }

    /** Inquilino do token já autenticado (claim {@code inq}); fallback padrão p/ tokens legados sem a claim. */
    private Long inquilinoDoToken(Jwt jwt) {
        Object inq = jwt.getClaim("inq");
        if (inq instanceof Number n) {
            return n.longValue();
        }
        return inquilinoService.idPadrao();
    }

    /** Restaura o schema anterior da thread (o valor cru, que pode ser {@code null}). */
    private void restaurar(String anterior) {
        if (anterior != null) {
            TenantContext.definir(anterior);
        } else {
            TenantContext.limpar();
        }
    }

    /**
     * Pré-checagem do login (SÓ CPF): diz se o CPF já tem senha (PIN) CENTRAL, para o app abrir a tela de
     * senha (tem) ou a de primeiro acesso (não tem). CLÍNICO-AGNÓSTICO (lê a identidade central no public),
     * então NÃO revela se o CPF é paciente desta clínica — a trava real de clínica fica no login-senha
     * (401 uniforme) e na conferência de identidade do SMS. Rate-limit por IP (anti-enumeração). Sem tenant.
     */
    @PostMapping("/pre-checagem")
    public PreChecagemResponse preChecagem(@Valid @RequestBody PreChecagemRequest request, HttpServletRequest http) {
        return new PreChecagemResponse(acessoService.preChecagemTemSenha(request.cpf(), ipDoCliente(http)));
    }

    /**
     * IP do cliente para o rate-limit. O proxy do Railway ANEXA o IP real do cliente à DIREITA do
     * X-Forwarded-For, então usamos o ÚLTIMO token não-vazio (os valores à esquerda são controlados
     * pelo cliente e seriam spoofáveis). Sem o header, cai no endereço remoto. Tolera tokens vazios
     * (ex.: header ",", que de outro modo quebraria o split[0]).
     */
    private static String ipDoCliente(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] partes = xff.split(",");
            for (int i = partes.length - 1; i >= 0; i--) {
                String ip = partes[i].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return http.getRemoteAddr();
    }

    /**
     * Início do login: confere a identidade (telefone+CPF+data) e informa se a conta já tem senha
     * (o app mostra o campo de senha) e se está bloqueada por tentativas. NÃO envia SMS.
     */
    @PostMapping("/iniciar")
    public InicioLoginResponse iniciar(@Valid @RequestBody IniciarLoginRequest request) {
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(inquilinoService.schemaPorId(resolverInquilino(request.cpf())));
        try {
            PacienteAcessoService.InicioLogin r =
                    acessoService.iniciar(request.cpf(), request.dataNascimento(), request.telefone());
            return new InicioLoginResponse(r.temSenha(), r.bloqueada());
        } finally {
            restaurar(anterior);
        }
    }

    /**
     * O usuário pede o código (SMS) informando CPF + telefone + data. No app A-baked confere a identidade na
     * clínica assada; no COMPARTILHADO confere em TODAS as clínicas do CPF (fixando o schema de cada uma) e
     * envia o OTP se ao menos uma bate, devolvendo as clínicas que conferem — o app escolhe ANTES de ativar
     * (o OTP é de uso único). 401 genérico (não enumera) se nenhuma confere.
     */
    @PostMapping("/solicitar-codigo")
    public SolicitarCodigoResponse solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequest request) {
        // (A) App A-baked: a clínica veio no X-Inquilino assado (o SubdominioTenantFilter já fixou o schema).
        String schemaHeader = TenantContext.atualBruto();
        Long inquilinoDoHeader = schemaHeader == null ? null : inquilinoService.idPorSchema(schemaHeader);
        if (inquilinoDoHeader != null) {
            String tel = acessoService.solicitarCodigo(request.cpf(), request.dataNascimento(), request.telefone());
            return new SolicitarCodigoResponse(tel, clinicasPorIds(List.of(inquilinoDoHeader)));
        }
        // (B) App compartilhado: confere a identidade EM CADA clínica do CPF; envia o OTP se ao menos uma bate.
        List<ClinicaLogin> clinicas = clinicasComIdentidade(request.cpf(), request.dataNascimento(), request.telefone());
        if (clinicas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_IDENTIDADE);
        }
        String tel = acessoService.enviarCodigoSemConferir(request.cpf(), request.telefone());
        return new SolicitarCodigoResponse(tel, clinicas);
    }

    /**
     * Login por SENHA (sem SMS): CPF + PIN. Devolve a SESSÃO quando a clínica já está definida (app A-baked
     * pelo X-Inquilino, OU 2ª chamada com a clínica escolhida); no app COMPARTILHADO sem clínica, confere o
     * PIN central e, se o CPF está em MAIS de uma clínica, devolve a lista pra escolher (sem sessão ainda).
     */
    @PostMapping("/login-senha")
    public LoginSenhaResponse loginSenha(@Valid @RequestBody LoginSenhaRequest request) {
        String dispositivoId = request.dispositivoId().trim();
        // (1) App compartilhado, 2ª chamada: a clínica foi ESCOLHIDA no seletor. O login-senha (checagem de
        // perfil na clínica) garante que o CPF é mesmo paciente lá — um id forjado dá 401, não vaza acesso.
        if (request.inquilinoId() != null) {
            return new LoginSenhaResponse(entrarNaClinica(request, dispositivoId, request.inquilinoId()), null);
        }
        // (2) App A-baked: a clínica veio no X-Inquilino assado (o SubdominioTenantFilter já fixou o schema).
        String schemaHeader = TenantContext.atualBruto();
        Long inquilinoDoHeader = schemaHeader == null ? null : inquilinoService.idPorSchema(schemaHeader);
        if (inquilinoDoHeader != null) {
            return new LoginSenhaResponse(entrarNaClinica(request, dispositivoId, inquilinoDoHeader), null);
        }
        // (3) App compartilhado, 1ª chamada (sem clínica): confere o PIN central ANTES de listar as clínicas
        // (não revela as clínicas do CPF sem autenticar — privacidade). Depois: 1 clínica entra direto, N escolhe.
        acessoService.verificarSenhaCentral(request.cpf(), request.senha()); // 401/429, sem sessão
        List<ClinicaLogin> clinicas = clinicasDoCpf(request.cpf());
        if (clinicas.isEmpty()) {
            // Autenticou, mas o CPF não é paciente em clínica nenhuma: mensagem uniforme (não enumera).
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "CPF ou senha inválidos.");
        }
        if (clinicas.size() == 1) {
            return new LoginSenhaResponse(entrarNaClinica(request, dispositivoId, clinicas.get(0).inquilinoId()), null);
        }
        return new LoginSenhaResponse(null, clinicas);
    }

    /** Clínicas ATIVAS que o CPF pode acessar (via os vínculos paciente_login do public). Vazio se nenhuma. */
    private List<ClinicaLogin> clinicasDoCpf(String cpfBruto) {
        String cpf = Documentos.somenteDigitos(cpfBruto);
        if (cpf == null || cpf.isEmpty()) {
            return List.of();
        }
        List<Long> ids = pacienteLoginRepository.findByCpf(cpf).stream()
                .map(PacienteLogin::getInquilinoId).distinct().toList();
        return clinicasPorIds(ids);
    }

    /** ClinicaLogin (id + nome) das clínicas ATIVAS dos ids dados. */
    private List<ClinicaLogin> clinicasPorIds(List<Long> ids) {
        return inquilinoService.clinicasAtivas(ids).stream()
                .map(c -> new ClinicaLogin(c.id(), c.nome())).toList();
    }

    /**
     * Clínicas (ATIVAS) do CPF onde a identidade (CPF + data + telefone) confere — fixa o schema de cada uma
     * e pergunta ao serviço. Para o app COMPARTILHADO escolher a clínica no fluxo de OTP (antes do ativar).
     */
    private List<ClinicaLogin> clinicasComIdentidade(String cpfBruto, LocalDate dataNascimento, String telefone) {
        String cpf = Documentos.somenteDigitos(cpfBruto);
        if (cpf == null || cpf.isEmpty()) {
            return List.of();
        }
        List<Long> ids = pacienteLoginRepository.findByCpf(cpf).stream()
                .map(PacienteLogin::getInquilinoId).distinct().toList();
        List<ClinicaLogin> confere = new ArrayList<>();
        String anterior = TenantContext.atualBruto();
        try {
            for (InquilinoService.Clinica clinica : inquilinoService.clinicasAtivas(ids)) {
                TenantContext.definir(inquilinoService.schemaPorId(clinica.id()));
                if (acessoService.identidadeConfereNoTenant(cpf, dataNascimento, telefone)) {
                    confere.add(new ClinicaLogin(clinica.id(), clinica.nome()));
                }
            }
        } finally {
            restaurar(anterior);
        }
        return confere;
    }

    /** Fixa o schema da clínica, faz o login por senha (PIN + perfil + sessão) e monta a resposta de sessão. */
    private AtivarResponse entrarNaClinica(LoginSenhaRequest request, String dispositivoId, Long inquilinoId) {
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(inquilinoService.schemaPorId(inquilinoId));
        try {
            ContaApp conta = acessoService.loginPorSenha(request.cpf(), request.senha(), dispositivoId);
            // Censo do vínculo (cpf→inquilino) desta clínica — idempotente; escreve no public.
            roteamentoService.registrar(conta.getCpf(), inquilinoId);
            return montarResposta(conta, dispositivoId, false, inquilinoId);
        } finally {
            restaurar(anterior);
        }
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
        // Clínica ESCOLHIDA no seletor (app compartilhado), senão o X-Inquilino assado / 1º vínculo. A
        // conferência de identidade do ativar (no schema fixado) garante que um id forjado dá 401.
        Long inquilinoId = request.inquilinoId() != null ? request.inquilinoId() : resolverInquilino(request.cpf());
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(inquilinoService.schemaPorId(inquilinoId));
        try {
            ContaApp conta = acessoService.ativar(request.cpf(), request.dataNascimento(), request.telefone(),
                    request.codigo(), dispositivoId);
            // Census do vínculo (cpf→inquilino) desta clínica (ver loginSenha): mantém o paciente_login
            // completo p/ a identidade central só ser apagada quando realmente não sobra clínica.
            roteamentoService.registrar(conta.getCpf(), inquilinoId);
            // Pós-OTP: a senha foi resetada — o app deve pedir um novo PIN antes de escolher o perfil.
            return montarResposta(conta, dispositivoId, true, inquilinoId);
        } finally {
            restaurar(anterior);
        }
    }

    /**
     * Clínicas (ATIVAS) que o CPF LOGADO pode acessar — para o app COMPARTILHADO oferecer "trocar unidade".
     * Pós-login (o próprio paciente vê as SUAS clínicas; a privacidade permite). Valida a sessão pelo token.
     */
    @GetMapping("/minhas-clinicas")
    public List<ClinicaLogin> minhasClinicas(@AuthenticationPrincipal Jwt jwt) {
        return clinicasDoCpf(acessoService.cpfDaSessao(jwt));
    }

    /**
     * Troca a clínica da sessão (app compartilhado): SEM refazer login, cria/atualiza a sessão na clínica
     * ALVO (perfil + conta_app) e reemite o token com o novo {@code inq}. Só para uma clínica vinculada ao
     * PRÓPRIO CPF (paciente_login) e onde ele tem perfil — senão 403/401.
     */
    @PostMapping("/trocar-clinica")
    public AtivarResponse trocarClinica(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrocarClinicaRequest request) {
        String cpf = acessoService.cpfDaSessao(jwt); // valida a sessão atual (cid + aparelho) e devolve o CPF
        String dispositivoId = jwt.getClaimAsString("dev");
        Long alvo = request.inquilinoId();
        // Segurança/consistência: só troca para uma clínica (a) vinculada ao próprio CPF (paciente_login) e
        // (b) ATIVA na plataforma — o mesmo filtro que /minhas-clinicas aplica, para a trava não aceitar uma
        // clínica que o seletor nunca ofereceria. Não confia no id cru do cliente. Senão 403.
        boolean vinculada = !pacienteLoginRepository.findByCpfAndInquilinoId(cpf, alvo).isEmpty();
        boolean ativa = !inquilinoService.clinicasAtivas(List.of(alvo)).isEmpty();
        if (!vinculada || !ativa) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clínica não disponível para este CPF.");
        }
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(inquilinoService.schemaPorId(alvo));
        try {
            ContaApp conta = acessoService.entrarNaClinicaSemSenha(cpf, dispositivoId);
            roteamentoService.registrar(conta.getCpf(), alvo);
            return montarResposta(conta, dispositivoId, false, alvo);
        } catch (ResponseStatusException e) {
            // O CPF está vinculado (paciente_login) mas não tem mais perfil ATIVO nesta clínica (ex.: foi
            // inativado/soft-deletado lá, ou era responsável e o vínculo foi inativado — o paciente_login não
            // é removido). entrarNaClinicaSemSenha devolve 401 nesse caso; aqui isso NÃO é "sessão expirada"
            // (a sessão atual é válida — cpfDaSessao passou), e sim "esta clínica não está disponível": remapeia
            // para 403, senão o app trata o 401 como logout e DESTRÓI a sessão válida da clínica atual.
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clínica não disponível para este CPF.");
            }
            throw e;
        } finally {
            restaurar(anterior);
        }
    }

    /** Monta a sessão (token do perfil padrão + lista de perfis) comum ao OTP e ao login por senha. */
    private AtivarResponse montarResposta(ContaApp conta, String dispositivoId, boolean precisaDefinirSenha,
            Long inquilinoId) {
        List<Perfil> perfis = acessoService.perfis(conta.getCpf());
        Perfil padrao = perfis.stream().filter(Perfil::proprio).findFirst()
                .orElse(perfis.isEmpty() ? null : perfis.get(0));
        if (padrao == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nenhum perfil disponível para este CPF");
        }
        String token = gerarToken(conta, padrao.paciente(), dispositivoId, inquilinoId);
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
        // Reemite o token mantendo o MESMO inquilino da sessão (o TenantFilter já fixou o schema pelo inq).
        String token = gerarToken(conta, perfil, jwt.getClaimAsString("dev"), inquilinoDoToken(jwt));
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

    /** Token do app: conta (cid) + perfil ativo (pid), amarrado ao aparelho (dev) e ao inquilino (inq). */
    private String gerarToken(ContaApp conta, Paciente perfil, String dispositivoId, Long inquilinoId) {
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
                .claim("inq", inquilinoId)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
