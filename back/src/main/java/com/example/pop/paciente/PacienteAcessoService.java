package com.example.pop.paciente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.push.Dispositivo;
import com.example.pop.push.DispositivoRepository;
import com.example.pop.verificacao.CanalVerificacao;
import com.example.pop.verificacao.VerificacaoService;

/**
 * Acesso do paciente ao app. O OTP autentica uma CONTA (o CPF) e amarra a sessão a
 * um único aparelho. Depois escolhe-se o PERFIL (paciente por quem se age): o próprio
 * (se o CPF é de um paciente) ou um dependente (se o CPF é de um responsável). Um CPF
 * que é só responsável também loga.
 *
 * <p>Identidade = CPF (único); o TELEFONE é só o canal por onde o OTP é enviado e pode
 * ser compartilhado (ex.: pai e filho no mesmo número). A verificação do código é
 * delegada ao provedor (Twilio Verify); o backend não gera nem guarda código.
 */
@Service
public class PacienteAcessoService {

    /** Intervalo mínimo entre dois envios para o mesmo CPF. */
    private static final long COOLDOWN_MS = 60_000L;
    /** Máximo de envios por CPF dentro da janela. */
    private static final int MAX_POR_JANELA = 5;
    private static final long JANELA_MS = 3_600_000L; // 1 hora

    /** Após este nº de PINs errados, o login por senha é bloqueado até um novo OTP (SMS). */
    private static final int MAX_TENTATIVAS_SENHA = 5;

    private final PacienteRepository repository;
    private final ContaAppRepository contaRepository;
    private final ResponsavelRepository responsavelRepository;
    private final DispositivoRepository dispositivoRepository;
    private final VerificacaoService verificacao;
    private final TelasAppService telasAppService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    /** Rate-limit por CPF (em memória) para evitar SMS bombing e abuso de custo. */
    private final Map<String, Deque<Long>> enviosPorCpf = new ConcurrentHashMap<>();

    public PacienteAcessoService(PacienteRepository repository, ContaAppRepository contaRepository,
            ResponsavelRepository responsavelRepository, DispositivoRepository dispositivoRepository,
            VerificacaoService verificacao, TelasAppService telasAppService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.contaRepository = contaRepository;
        this.responsavelRepository = responsavelRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.verificacao = verificacao;
        this.telasAppService = telasAppService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Resultado do início do login: se a conta já tem senha e se o login por senha está bloqueado. */
    public record InicioLogin(boolean temSenha, boolean bloqueada) {
    }

    /**
     * Início do login: confere a identidade (telefone+CPF+data) e informa se a conta já tem senha
     * (para o app mostrar o campo de senha) e se está bloqueada por tentativas. NÃO envia SMS. 401
     * genérico se a identidade não confere (sem enumeração de CPF).
     */
    @Transactional(readOnly = true)
    public InicioLogin iniciar(String cpfBruto, LocalDate dataNascimento, String telefoneBruto) {
        String cpf = normalizarCpf(cpfBruto);
        conferirIdentidade(cpf, dataNascimento, telefoneBruto);
        ContaApp conta = contaRepository.findByCpf(cpf).orElse(null);
        boolean temSenha = conta != null && conta.getSenhaHash() != null;
        boolean bloqueada = conta != null && conta.getSenhaTentativas() >= MAX_TENTATIVAS_SENHA;
        return new InicioLogin(temSenha, bloqueada);
    }

    /**
     * Login por SENHA (sem SMS): confere a identidade + o PIN e amarra o aparelho. Bloqueia após
     * {@link #MAX_TENTATIVAS_SENHA} erros (aí só por OTP). 401 genérico na identidade.
     */
    @Transactional
    public ContaApp loginPorSenha(String cpfBruto, LocalDate dataNascimento, String telefoneBruto, String pin,
            String dispositivoId) {
        String cpf = normalizarCpf(cpfBruto);
        conferirIdentidade(cpf, dataNascimento, telefoneBruto);
        ContaApp conta = contaRepository.findByCpf(cpf).orElse(null);
        if (conta == null || conta.getSenhaHash() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Você ainda não cadastrou uma senha. Entre com o código por SMS.");
        }
        if (conta.getSenhaTentativas() >= MAX_TENTATIVAS_SENHA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Entre com o código por SMS para redefinir a senha.");
        }
        if (pin == null || !passwordEncoder.matches(pin, conta.getSenhaHash())) {
            conta.setSenhaTentativas(conta.getSenhaTentativas() + 1);
            conta.setAtualizadoEm(LocalDateTime.now());
            contaRepository.save(conta);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta.");
        }
        conta.setSenhaTentativas(0);
        conta.setDispositivoAtivo(dispositivoId);
        conta.setAtualizadoEm(LocalDateTime.now());
        contaRepository.save(conta);
        // Compat: espelha ativo/dispositivo no paciente próprio (chat/WebSocket/dashboard leem isso).
        repository.findByCpf(cpf).ifPresent(p -> {
            p.setAtivo(true);
            p.setDispositivoAtivo(dispositivoId);
            repository.save(p);
        });
        return conta;
    }

    /** Define a senha inicial (pós-OTP, PIN de 6 dígitos). Exige que a conta ainda NÃO tenha senha. */
    @Transactional
    public void definirSenhaInicial(Long contaId, String dispositivoId, String pin) {
        ContaApp conta = contaValidaPorId(contaId, dispositivoId);
        if (conta.getSenhaHash() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você já tem uma senha. Use 'Alterar senha' no seu perfil.");
        }
        validarPin(pin);
        conta.setSenhaHash(passwordEncoder.encode(pin));
        conta.setSenhaTentativas(0);
        conta.setAtualizadoEm(LocalDateTime.now());
        contaRepository.save(conta);
    }

    /** Altera a senha logado (Meu Perfil): exige a senha atual quando já existe uma. */
    @Transactional
    public void alterarSenha(Long contaId, String dispositivoId, String pinAtual, String pinNovo) {
        ContaApp conta = contaValidaPorId(contaId, dispositivoId);
        validarPin(pinNovo);
        if (conta.getSenhaHash() != null
                && (pinAtual == null || !passwordEncoder.matches(pinAtual, conta.getSenhaHash()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta.");
        }
        conta.setSenhaHash(passwordEncoder.encode(pinNovo));
        conta.setSenhaTentativas(0);
        conta.setAtualizadoEm(LocalDateTime.now());
        contaRepository.save(conta);
    }

    /** A conta do CPF já tem senha definida? (para o app mostrar "Definir" ou "Alterar" no perfil). */
    @Transactional(readOnly = true)
    public boolean contaTemSenha(String cpf) {
        return contaRepository.findByCpf(normalizarCpf(cpf)).map(c -> c.getSenhaHash() != null).orElse(false);
    }

    /** PIN de acesso: exatamente 6 dígitos numéricos. */
    private static void validarPin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A senha deve ter 6 dígitos numéricos.");
        }
    }

    /** Normaliza o telefone para apenas dígitos. */
    public static String normalizarTelefone(String telefone) {
        return telefone == null ? null : telefone.replaceAll("\\D", "");
    }

    /** Normaliza o CPF para apenas dígitos. */
    private static String normalizarCpf(String cpf) {
        return Documentos.somenteDigitos(cpf);
    }

    /**
     * Um perfil acessível pela conta: o paciente, se é o perfil próprio ou um dependente,
     * e (para dependentes) as permissões do responsável por funcionalidade. Perfil próprio
     * não tem trava (mapa vazio; o app trata próprio como acesso total).
     */
    public record Perfil(Paciente paciente, boolean proprio,
            Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
    }

    /** Destino de um push direcionado: o nome do perfil e as contas que o alcançam. */
    public record DestinoPush(Long pacienteId, String nome, List<Long> contaIds) {
    }

    /**
     * Contas (aparelhos) que devem receber o push de um paciente: a própria conta
     * do paciente (mesmo CPF) + as contas de todos os seus responsáveis. Assim a
     * notificação chega mesmo com a conta logada em outro perfil.
     */
    @Transactional(readOnly = true)
    public DestinoPush destinoPush(Long pacienteId) {
        return destinoPush(pacienteId, null);
    }

    /**
     * Variante que respeita as permissões: quando {@code funcionalidade} não é nula, um
     * responsável só entra no fan-out se tiver ao menos VISUALIZAR nela (suprime o push de
     * quem não acessa aquela funcionalidade). O próprio paciente sempre recebe. Nula = sem filtro.
     */
    @Transactional(readOnly = true)
    public DestinoPush destinoPush(Long pacienteId, FuncionalidadeApp funcionalidade) {
        // Kill switch global: tela desligada → ninguém recebe push dela.
        if (funcionalidade != null && !telasAppService.habilitada(funcionalidade)) {
            return new DestinoPush(pacienteId, null, List.of());
        }
        Paciente p = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (p == null) {
            return new DestinoPush(pacienteId, null, List.of());
        }
        Set<String> cpfs = new LinkedHashSet<>();
        String proprio = p.getCpf(); // já é só dígitos
        if (proprio != null && !proprio.isEmpty()) {
            cpfs.add(proprio); // o próprio paciente sempre recebe
        }
        for (Responsavel r : responsavelRepository.findByPaciente_Id(pacienteId)) {
            // Responsável inativo perdeu o acesso ao perfil: não recebe o push (senão a
            // prévia da notificação — atividade do paciente — vazaria para quem já não acessa).
            if (!r.isAtivo()) {
                continue;
            }
            String cpf = r.getCpf();
            if (cpf == null || cpf.isEmpty()) {
                continue;
            }
            boolean acessa = funcionalidade == null
                    || r.getPermissoes().getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO)
                            != NivelAcessoResponsavel.SEM_ACESSO;
            if (acessa) {
                cpfs.add(cpf);
            }
        }
        List<Long> contaIds = cpfs.isEmpty() ? List.of() : contaRepository.findIdsByCpfIn(cpfs);
        return new DestinoPush(pacienteId, p.getNome(), contaIds);
    }

    /**
     * O paciente está ALCANÇÁVEL no app: ou a própria sessão está ativa (aparelho
     * vinculado), ou algum responsável dele tem uma conta com aparelho ativo — nesse
     * caso o responsável pode responder por ele e o admin pode enviar/abrir conversa.
     */
    @Transactional(readOnly = true)
    public boolean pacienteAlcancavel(Paciente paciente) {
        if (paciente == null) {
            return false;
        }
        if (paciente.isAtivo() && paciente.getDispositivoAtivo() != null) {
            return true; // sessão própria ativa
        }
        Set<String> cpfs = new LinkedHashSet<>();
        for (String cpf : responsavelRepository.cpfsDosResponsaveis(paciente.getId())) {
            if (cpf != null && !cpf.isEmpty()) {
                cpfs.add(cpf);
            }
        }
        return !cpfs.isEmpty() && contaRepository.existeSessaoAtivaPorCpfs(cpfs);
    }

    /**
     * Revalida a sessão do paciente para o WebSocket (chamada a cada assinatura no chat):
     * valida a conta pelo aparelho e confere que o perfil (pid) pertence a ela — o perfil
     * dependente também passa. Lança 401 se a sessão não vale mais.
     */
    public void revalidarSessaoPaciente(Long contaId, Long pacienteId, String dispositivoId) {
        if (contaId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        ContaApp conta = contaValidaPorId(contaId, dispositivoId);
        perfilDaConta(conta.getCpf(), pacienteId); // 401 se o perfil não pertence à conta
    }

    /** Mensagem GENÉRICA: não revela qual campo errou nem se o CPF existe (evita enumeração). */
    private static final String MSG_IDENTIDADE =
            "Telefone, CPF ou data de nascimento não confere. Verifique os dados ou procure a sua unidade de saúde.";

    /**
     * Envia o código (SMS) para o telefone DIGITADO no login, após conferir a identidade
     * (Telefone + CPF + data de nascimento). Devolve o telefone mascarado. Se a identidade
     * não confere, lança 401 genérico e NÃO envia nada (não vira disparador de SMS).
     */
    public String solicitarCodigo(String cpfBruto, LocalDate dataNascimento, String telefoneBruto) {
        String cpf = normalizarCpf(cpfBruto);
        conferirIdentidade(cpf, dataNascimento, telefoneBruto);
        checarLimiteEnvio(cpf);
        verificacao.enviar(e164(telefoneBruto), CanalVerificacao.SMS);
        return mascararTelefone(telefoneBruto);
    }

    /**
     * Confere a identidade do login: CPF + data de nascimento + telefone digitado. O telefone
     * precisa pertencer ao dono do CPF — para o PACIENTE, ser um dos telefones da lista dele;
     * para o RESPONSÁVEL (cadastro ATIVO), ser o telefone dele. A comparação é TOLERANTE
     * ({@link #telefoneCanonico}: ignora o 55 do país e o 9 extra do celular). Erro GENÉRICO
     * (401) se qualquer parte não confere — sem revelar qual campo falhou.
     */
    private void conferirIdentidade(String cpf, LocalDate dataNascimento, String telefoneBruto) {
        String alvo = telefoneCanonico(telefoneBruto);
        if (cpf == null || cpf.isEmpty() || dataNascimento == null || alvo == null
                || !identidadeConfere(cpf, dataNascimento, alvo)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_IDENTIDADE);
        }
    }

    /**
     * true quando o CPF + data + telefone (canônico) batem com um paciente (telefone na sua
     * lista) ou com um responsável ATIVO (o telefone dele). Escopo sempre pelo CPF: um número
     * pode estar em vários cadastros (família), então nunca se resolve só pelo telefone.
     */
    private boolean identidadeConfere(String cpf, LocalDate dataNascimento, String telefoneCanonicoAlvo) {
        Paciente paciente = repository.findByCpf(cpf).orElse(null);
        if (paciente != null) {
            return dataNascimento.equals(paciente.getDataNascimento())
                    && telefonesCanonicos(paciente.getTelefonesAdicionais()).contains(telefoneCanonicoAlvo);
        }
        for (Responsavel r : responsavelRepository.findByCpfAndAtivoTrue(cpf)) {
            if (dataNascimento.equals(r.getDataNascimento())
                    && telefoneCanonicoAlvo.equals(telefoneCanonico(r.getTelefone()))) {
                return true;
            }
        }
        return false;
    }

    /** Conjunto de telefones canônicos de uma lista (sem nulos), para a conferência do login. */
    private static Set<String> telefonesCanonicos(List<String> telefones) {
        Set<String> canonicos = new LinkedHashSet<>();
        if (telefones != null) {
            for (String t : telefones) {
                String c = telefoneCanonico(t);
                if (c != null) {
                    canonicos.add(c);
                }
            }
        }
        return canonicos;
    }

    /**
     * Forma canônica para COMPARAR telefones com tolerância (o XML e o que o paciente digita
     * vêm em formatos variados): só dígitos, sem o código do país 55 e sem o 9 extra do
     * celular — reduz a "DDD + 8 dígitos finais". {@code null}/vazio → {@code null}.
     */
    static String telefoneCanonico(String telefone) {
        String d = normalizarTelefone(telefone);
        if (d == null || d.isEmpty()) {
            return null;
        }
        if (d.startsWith("55") && d.length() >= 12) {
            d = d.substring(2); // tira o código do país de números "longos"
        }
        if (d.length() == 11 && d.charAt(2) == '9') {
            d = d.substring(0, 2) + d.substring(3); // descarta o 9 extra do celular
        }
        return d;
    }

    /** Bloqueia envios em excesso para o mesmo CPF (cooldown + teto por hora) → 429. */
    private void checarLimiteEnvio(String cpf) {
        long agora = System.currentTimeMillis();
        Deque<Long> janela = enviosPorCpf.computeIfAbsent(cpf, k -> new ArrayDeque<>());
        synchronized (janela) {
            while (!janela.isEmpty() && agora - janela.peekFirst() > JANELA_MS) {
                janela.pollFirst();
            }
            if (!janela.isEmpty() && agora - janela.peekLast() < COOLDOWN_MS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Aguarde um momento antes de pedir um novo código.");
            }
            if (janela.size() >= MAX_POR_JANELA) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitos pedidos de código. Tente novamente mais tarde.");
            }
            janela.addLast(agora);
        }
    }

    /**
     * Confere o código (via provedor) e amarra a CONTA (CPF) ao aparelho, invalidando o
     * anterior. Lança 401 se o código não confere.
     *
     * <p>Compat: se o CPF é de um paciente, também grava ativo/dispositivo no paciente
     * próprio — o chat/WebSocket e o dashboard ("usando o app") ainda leem isso.
     * Transacional para conta e paciente ficarem consistentes (all-or-nothing).
     */
    @Transactional
    public ContaApp ativar(String cpfBruto, LocalDate dataNascimento, String telefoneBruto, String codigo,
            String dispositivoId) {
        String cpf = normalizarCpf(cpfBruto);
        conferirIdentidade(cpf, dataNascimento, telefoneBruto); // 401 genérico se não confere
        if (!verificacao.checar(e164(telefoneBruto), codigo)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "CPF ou código inválido");
        }
        ContaApp conta = upsertConta(cpf, dispositivoId);
        // O OTP redefine a senha: limpa o PIN e as tentativas — o app pede um novo PIN em seguida.
        // Cobre tanto o 1º acesso quanto o "esqueci a senha" de forma uniforme.
        conta.setSenhaHash(null);
        conta.setSenhaTentativas(0);
        contaRepository.save(conta);
        repository.findByCpf(cpf).ifPresent(p -> {
            p.setAtivo(true);
            p.setDispositivoAtivo(dispositivoId);
            repository.save(p);
        });
        return conta;
    }

    /** Cria/atualiza a conta do CPF com o aparelho atual. */
    private ContaApp upsertConta(String cpf, String dispositivoId) {
        LocalDateTime agora = LocalDateTime.now();
        ContaApp conta = contaRepository.findByCpf(cpf).orElseGet(() -> {
            ContaApp nova = new ContaApp();
            nova.setCpf(cpf);
            nova.setCriadoEm(agora);
            return nova;
        });
        conta.setDispositivoAtivo(dispositivoId);
        conta.setAtualizadoEm(agora);
        return contaRepository.save(conta);
    }

    /**
     * Perfis acessíveis por um CPF: o próprio (se for paciente) primeiro, e depois cada
     * dependente (paciente de quem o CPF é responsável). Sem repetir.
     */
    public List<Perfil> perfis(String cpfBruto) {
        String cpf = normalizarCpf(cpfBruto);
        Map<Long, Perfil> porId = new LinkedHashMap<>();
        if (cpf != null && !cpf.isEmpty()) {
            // Cadastros inativos (soft-delete) não aparecem no seletor de perfis.
            repository.findByCpf(cpf)
                    .filter(p -> p.getSituacao() != SituacaoCadastro.INATIVO)
                    .ifPresent(p -> porId.put(p.getId(), new Perfil(p, true, Map.of())));
            for (Paciente dep : responsavelRepository.pacientesPorCpfDoResponsavel(cpf)) {
                if (dep.getSituacao() == SituacaoCadastro.INATIVO) {
                    continue;
                }
                porId.computeIfAbsent(dep.getId(), k -> new Perfil(dep, false, permissoesDoResponsavel(dep.getId(), cpf)));
            }
        }
        return List.copyOf(porId.values());
    }

    /** Permissões (por funcionalidade) do responsável deste paciente com este CPF; vazio se não achar. */
    private Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoesDoResponsavel(Long pacienteId, String cpf) {
        return responsavelRepository.findFirstByPaciente_IdAndCpfOrderByIdAsc(pacienteId, cpf)
                .map(Responsavel::getPermissoes)
                .orElseGet(Map::of);
    }

    /**
     * Resolve e valida o paciente logado a partir do token do app: é a ÚNICA fonte do
     * perfil ativo nos endpoints /meu/**. Valida a conta pelo cid e confere que o pid
     * pertence a ela (próprio ou dependente).
     */
    public Paciente pacienteDoToken(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        Object cid = jwt.getClaim("cid");
        if (!(pid instanceof Number pidNum) || !(cid instanceof Number cidNum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        ContaApp conta = contaValidaPorId(cidNum.longValue(), jwt.getClaimAsString("dev"));
        return perfilDaConta(conta.getCpf(), pidNum.longValue());
    }

    /** CPF da sessão a partir do token (para listar perfis). Valida a conta pelo cid + aparelho. */
    public String cpfDaSessao(Jwt jwt) {
        Object cid = jwt.getClaim("cid");
        if (!(cid instanceof Number cidNum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return contaValidaPorId(cidNum.longValue(), jwt.getClaimAsString("dev")).getCpf();
    }

    /**
     * O responsável (cadastro) que a sessão está representando ao agir por um dependente:
     * quando o CPF da conta é responsável do perfil ativo (pid). Vazio quando é o perfil
     * próprio (mesmo CPF) — aí o comentário é do paciente.
     */
    public Optional<Responsavel> responsavelDaSessao(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number pidNum)) {
            return Optional.empty();
        }
        String cpf = cpfDaSessao(jwt); // valida a sessão e devolve o CPF da conta
        Paciente perfil = repository.findById(pidNum.longValue()).orElse(null);
        if (cpf == null || cpf.isEmpty() || perfil == null) {
            return Optional.empty();
        }
        if (cpf.equals(perfil.getCpf())) {
            return Optional.empty(); // perfil próprio: não é "via responsável"
        }
        return responsavelRepository.findFirstByPaciente_IdAndCpfOrderByIdAsc(pidNum.longValue(), cpf);
    }

    /**
     * Nível de acesso da sessão a uma funcionalidade do app. Perfil PRÓPRIO (a sessão
     * não age por um responsável) tem acesso total; perfil dependente usa as permissões
     * do responsável, com ausência = SEM_ACESSO (padrão).
     */
    public NivelAcessoResponsavel nivelDaSessao(Jwt jwt, FuncionalidadeApp funcionalidade) {
        // Kill switch global: tela desligada → SEM_ACESSO para TODOS (inclusive o perfil próprio).
        if (!telasAppService.habilitada(funcionalidade)) {
            return NivelAcessoResponsavel.SEM_ACESSO;
        }
        Responsavel responsavel = responsavelDaSessao(jwt).orElse(null);
        if (responsavel == null) {
            return NivelAcessoResponsavel.VISUALIZAR_LANCAR; // perfil próprio: sem trava
        }
        return responsavel.getPermissoes().getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO);
    }

    /** 403 quando a sessão não pode nem VISUALIZAR a funcionalidade. */
    public void exigirVisualizar(Jwt jwt, FuncionalidadeApp funcionalidade) {
        if (nivelDaSessao(jwt, funcionalidade) == NivelAcessoResponsavel.SEM_ACESSO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O responsável não tem acesso a esta funcionalidade.");
        }
    }

    /** 403 quando a sessão não pode fazer lançamentos (precisa de VISUALIZAR_LANCAR). */
    public void exigirLancar(Jwt jwt, FuncionalidadeApp funcionalidade) {
        if (nivelDaSessao(jwt, funcionalidade) != NivelAcessoResponsavel.VISUALIZAR_LANCAR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O responsável não tem permissão para lançamentos nesta funcionalidade.");
        }
    }

    /** Ids das unidades de saúde que o paciente pode acessar (feed/chat/SAU). Vazio = sem acesso. */
    public Set<Long> unidadeIdsDoPaciente(Paciente paciente) {
        if (paciente == null || paciente.getUnidades() == null) {
            return Set.of();
        }
        return paciente.getUnidades().stream()
                .map(com.example.pop.unidade.Unidade::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 403 quando a unidade não está entre as que o paciente pode acessar. */
    public void exigirUnidade(Paciente paciente, Long unidadeId) {
        if (unidadeId == null || !unidadeIdsDoPaciente(paciente).contains(unidadeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você não tem acesso a esta unidade de saúde.");
        }
    }

    /**
     * Nível da (conta, perfil) numa funcionalidade — para uso fora do fluxo de token,
     * como o WebSocket, cujo principal só carrega cid+pid. Perfil próprio → acesso total;
     * dependente → permissões do responsável (ausente = SEM_ACESSO). Sessão inválida
     * (sem conta/perfil) → SEM_ACESSO.
     */
    public NivelAcessoResponsavel nivelPorContaEPerfil(Long contaId, Long pacienteId, FuncionalidadeApp funcionalidade) {
        // Kill switch global: tela desligada → SEM_ACESSO para todos (cobre o WebSocket do chat).
        if (!telasAppService.habilitada(funcionalidade)) {
            return NivelAcessoResponsavel.SEM_ACESSO;
        }
        ContaApp conta = contaId == null ? null : contaRepository.findById(contaId).orElse(null);
        Paciente perfil = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (conta == null || perfil == null) {
            return NivelAcessoResponsavel.SEM_ACESSO;
        }
        if (conta.getCpf() != null && conta.getCpf().equals(perfil.getCpf())) {
            return NivelAcessoResponsavel.VISUALIZAR_LANCAR; // perfil próprio
        }
        return responsavelRepository.findFirstByPaciente_IdAndCpfOrderByIdAsc(pacienteId, conta.getCpf())
                .map(r -> r.getPermissoes().getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO))
                .orElse(NivelAcessoResponsavel.SEM_ACESSO);
    }

    /** Conta da sessão para trocar de perfil (valida a conta pelo cid + aparelho). */
    public ContaApp contaParaTroca(Jwt jwt) {
        Object cid = jwt.getClaim("cid");
        if (!(cid instanceof Number cidNum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return contaValidaPorId(cidNum.longValue(), jwt.getClaimAsString("dev"));
    }

    /** Valida a conta (cid) e confere que o aparelho é o vinculado. */
    private ContaApp contaValidaPorId(Long contaId, String dispositivoId) {
        ContaApp conta = contaId == null ? null : contaRepository.findById(contaId).orElse(null);
        if (conta == null || dispositivoId == null || !dispositivoId.equals(conta.getDispositivoAtivo())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return conta;
    }

    /** O paciente do perfil pedido, desde que pertença à conta (próprio ou dependente). */
    public Paciente perfilDaConta(String cpfConta, Long pacienteId) {
        Paciente paciente = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (paciente == null || !perfilPertenceAConta(cpfConta, paciente)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Perfil não disponível para esta conta");
        }
        return paciente;
    }

    /** O paciente é o próprio (mesmo CPF) ou um dependente (o CPF é responsável dele). */
    private boolean perfilPertenceAConta(String cpfConta, Paciente paciente) {
        if (cpfConta == null || cpfConta.isEmpty()) {
            return false;
        }
        // Cadastro inativo (soft-delete): fora do app para QUALQUER acesso — próprio ou via
        // responsável. Sem isto, um responsável com sessão ativa seguiria vendo/agindo no
        // perfil inativado.
        if (paciente.getSituacao() == SituacaoCadastro.INATIVO) {
            return false;
        }
        if (cpfConta.equals(paciente.getCpf())) {
            // Perfil próprio: respeita a revogação administrativa (ativo=false → sem acesso).
            return paciente.isAtivo();
        }
        // Dependente: só um responsável ATIVO dá acesso (inativo perde acesso ao perfil).
        return responsavelRepository.existsByCpfAndPaciente_IdAndAtivoTrue(cpfConta, paciente.getId());
    }

    /**
     * Revoga o acesso: desloga o aparelho atual (o paciente pode reativar por OTP).
     * Encerra a sessão do paciente (campos legados) e a conta (do CPF) — senão um token
     * com cid seguiria válido em /meu/**.
     */
    public void revogar(Paciente paciente) {
        paciente.setAtivo(false);
        paciente.setDispositivoAtivo(null);
        repository.save(paciente);
        String cpf = paciente.getCpf();
        if (cpf != null && !cpf.isEmpty()) {
            contaRepository.findByCpf(cpf).ifPresent(conta -> {
                conta.setDispositivoAtivo(null);
                conta.setAtualizadoEm(LocalDateTime.now());
                contaRepository.save(conta);
                // Corta também o push: desvincula os aparelhos desta conta.
                List<Dispositivo> aparelhos = dispositivoRepository.findByContaId(conta.getId());
                aparelhos.forEach(d -> {
                    d.setContaId(null);
                    d.setPacienteId(null);
                });
                dispositivoRepository.saveAll(aparelhos);
            });
        }
    }

    /** Monta o E.164 assumindo Brasil (+55) quando o número não vem com o país. */
    static String e164(String telefone) {
        String d = normalizarTelefone(telefone);
        if (d == null || d.isEmpty()) {
            return null;
        }
        if (d.startsWith("55") && d.length() >= 12) {
            return "+" + d;
        }
        return "+55" + d;
    }

    /** Telefone mascarado para exibição (só os 4 últimos dígitos). */
    static String mascararTelefone(String telefone) {
        String d = normalizarTelefone(telefone);
        if (d == null || d.length() < 4) {
            return "••••";
        }
        return "(••) •••••-" + d.substring(d.length() - 4);
    }
}
