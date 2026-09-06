package com.example.pop.paciente;

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
 * Acesso do paciente ao app. O OTP autentica uma CONTA (o telefone) e amarra a
 * sessão a um único aparelho. Depois escolhe-se o PERFIL (paciente por quem se
 * age): o próprio (se o telefone for de um paciente) ou um dependente (se o
 * telefone for de um responsável). Um telefone que é só responsável também loga.
 *
 * <p>A verificação do código é delegada ao provedor (Twilio Verify); o backend
 * não gera nem guarda código. O envio por WhatsApp segue suportado no
 * {@link VerificacaoService}, mas está desativado no fluxo (canal fixo em SMS).
 */
@Service
public class PacienteAcessoService {

    /** Intervalo mínimo entre dois envios para o mesmo telefone. */
    private static final long COOLDOWN_MS = 60_000L;
    /** Máximo de envios por telefone dentro da janela. */
    private static final int MAX_POR_JANELA = 5;
    private static final long JANELA_MS = 3_600_000L; // 1 hora

    private final PacienteRepository repository;
    private final ContaAppRepository contaRepository;
    private final ResponsavelRepository responsavelRepository;
    private final DispositivoRepository dispositivoRepository;
    private final VerificacaoService verificacao;
    /** Rate-limit por telefone (em memória) para evitar SMS bombing e abuso de custo. */
    private final Map<String, Deque<Long>> enviosPorTelefone = new ConcurrentHashMap<>();

    public PacienteAcessoService(PacienteRepository repository, ContaAppRepository contaRepository,
            ResponsavelRepository responsavelRepository, DispositivoRepository dispositivoRepository,
            VerificacaoService verificacao) {
        this.repository = repository;
        this.contaRepository = contaRepository;
        this.responsavelRepository = responsavelRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.verificacao = verificacao;
    }

    /** Normaliza o telefone para apenas dígitos. */
    public static String normalizarTelefone(String telefone) {
        return telefone == null ? null : telefone.replaceAll("\\D", "");
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
     * do paciente (mesmo telefone) + as contas de todos os seus responsáveis. Assim
     * a notificação chega mesmo com a conta logada em outro perfil.
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
        Paciente p = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (p == null) {
            return new DestinoPush(pacienteId, null, List.of());
        }
        Set<String> telefones = new LinkedHashSet<>();
        String proprio = normalizarTelefone(p.getTelefone());
        if (proprio != null && !proprio.isEmpty()) {
            telefones.add(proprio); // o próprio paciente sempre recebe
        }
        for (Responsavel r : responsavelRepository.findByPaciente_Id(pacienteId)) {
            String d = normalizarTelefone(r.getTelefone());
            if (d == null || d.isEmpty()) {
                continue;
            }
            boolean acessa = funcionalidade == null
                    || r.getPermissoes().getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO)
                            != NivelAcessoResponsavel.SEM_ACESSO;
            if (acessa) {
                telefones.add(d);
            }
        }
        List<Long> contaIds = telefones.isEmpty() ? List.of() : contaRepository.findIdsByTelefoneIn(telefones);
        return new DestinoPush(pacienteId, p.getNome(), contaIds);
    }

    /**
     * O paciente está ALCANÇÁVEL no app: ou a própria sessão está ativa (aparelho
     * vinculado), ou algum responsável dele tem uma conta com aparelho ativo — nesse
     * caso o responsável pode responder por ele e o admin pode enviar/abrir conversa.
     * Base da regra do chat (substitui o antigo "paciente está usando o app").
     */
    @Transactional(readOnly = true)
    public boolean pacienteAlcancavel(Paciente paciente) {
        if (paciente == null) {
            return false;
        }
        if (paciente.isAtivo() && paciente.getDispositivoAtivo() != null) {
            return true; // sessão própria ativa
        }
        Set<String> telefones = new LinkedHashSet<>();
        for (String tel : responsavelRepository.telefonesDosResponsaveis(paciente.getId())) {
            String d = normalizarTelefone(tel);
            if (d != null && !d.isEmpty()) {
                telefones.add(d);
            }
        }
        return !telefones.isEmpty() && contaRepository.existeSessaoAtivaPorTelefones(telefones);
    }

    /**
     * Revalida a sessão do paciente para o WebSocket (chamada a cada assinatura no
     * chat). Token novo (cid): valida a conta pelo aparelho e confere que o perfil
     * (pid) pertence a ela — o perfil dependente também passa. Token antigo (sem
     * cid): sessão do próprio paciente (legado). Lança 401 se a sessão não vale mais.
     */
    public void revalidarSessaoPaciente(Long contaId, Long pacienteId, String dispositivoId) {
        if (contaId != null) {
            ContaApp conta = contaValidaPorId(contaId, dispositivoId);
            perfilDaConta(conta.getTelefone(), pacienteId); // 401 se o perfil não pertence à conta
            return;
        }
        validarSessao(pacienteId, dispositivoId);
    }

    /**
     * Envia o código (SMS) para o telefone. O telefone precisa pertencer a um
     * paciente OU a um responsável cadastrado; senão → 404.
     */
    public void solicitarCodigo(String telefone) {
        String tel = normalizarTelefone(telefone);
        if (tel == null || tel.isEmpty() || !existeContaParaTelefone(tel)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Telefone não encontrado no cadastro. Entre em contato com a sua unidade de saúde.");
        }
        checarLimiteEnvio(tel);
        verificacao.enviar(e164(tel), CanalVerificacao.SMS);
    }

    /** O telefone é de um paciente ou de um responsável (base do login). */
    private boolean existeContaParaTelefone(String tel) {
        return repository.existsByTelefone(tel) || responsavelRepository.existsByTelefone(tel);
    }

    /** Bloqueia envios em excesso para o mesmo telefone (cooldown + teto por hora) → 429. */
    private void checarLimiteEnvio(String telefone) {
        long agora = System.currentTimeMillis();
        Deque<Long> janela = enviosPorTelefone.computeIfAbsent(telefone, k -> new ArrayDeque<>());
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
     * Confere o código (via provedor) e amarra a CONTA (telefone) ao aparelho,
     * invalidando o anterior. Lança 401 se telefone/código não conferem.
     *
     * <p>Compat: se o telefone é de um paciente, também grava ativo/dispositivo no
     * paciente próprio — o chat/WebSocket ainda leem isso no perfil próprio.
     * Transacional para conta e paciente ficarem consistentes (all-or-nothing).
     */
    @Transactional
    public ContaApp ativar(String telefone, String codigo, String dispositivoId) {
        String tel = normalizarTelefone(telefone);
        boolean aprovado = tel != null && !tel.isEmpty() && existeContaParaTelefone(tel)
                && verificacao.checar(e164(tel), codigo);
        if (!aprovado) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telefone ou código inválido");
        }
        ContaApp conta = upsertConta(tel, dispositivoId);
        repository.findByTelefone(tel).ifPresent(p -> {
            p.setAtivo(true);
            p.setDispositivoAtivo(dispositivoId);
            repository.save(p);
        });
        return conta;
    }

    /** Cria/atualiza a conta do telefone com o aparelho atual. */
    private ContaApp upsertConta(String tel, String dispositivoId) {
        LocalDateTime agora = LocalDateTime.now();
        ContaApp conta = contaRepository.findByTelefone(tel).orElseGet(() -> {
            ContaApp nova = new ContaApp();
            nova.setTelefone(tel);
            nova.setCriadoEm(agora);
            return nova;
        });
        conta.setDispositivoAtivo(dispositivoId);
        conta.setAtualizadoEm(agora);
        return contaRepository.save(conta);
    }

    /**
     * Perfis acessíveis por um telefone: o próprio (se for paciente) primeiro, e
     * depois cada dependente (paciente de quem o telefone é responsável). Sem repetir.
     */
    public List<Perfil> perfis(String telefone) {
        String tel = normalizarTelefone(telefone);
        Map<Long, Perfil> porId = new LinkedHashMap<>();
        if (tel != null && !tel.isEmpty()) {
            repository.findByTelefone(tel).ifPresent(p -> porId.put(p.getId(), new Perfil(p, true, Map.of())));
            for (Paciente dep : responsavelRepository.pacientesPorTelefoneDoResponsavel(tel)) {
                porId.computeIfAbsent(dep.getId(), k -> new Perfil(dep, false, permissoesDoResponsavel(dep.getId(), tel)));
            }
        }
        return List.copyOf(porId.values());
    }

    /** Permissões (por funcionalidade) do responsável deste paciente com este telefone; vazio se não achar. */
    private Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoesDoResponsavel(Long pacienteId, String telefone) {
        return responsavelRepository.findFirstByPaciente_IdAndTelefoneOrderByIdAsc(pacienteId, telefone)
                .map(Responsavel::getPermissoes)
                .orElseGet(Map::of);
    }

    /**
     * Resolve e valida o paciente logado a partir do token do app: é a ÚNICA fonte
     * do perfil ativo nos endpoints /meu/**. Token novo (com cid) valida a conta e
     * confere que o pid pertence a ela; token antigo (sem cid) usa a sessão do
     * próprio paciente (compat — ninguém é deslogado por um deploy).
     */
    public Paciente pacienteDoToken(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number pidNum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        Object cid = jwt.getClaim("cid");
        if (cid instanceof Number cidNum) {
            ContaApp conta = contaValidaPorId(cidNum.longValue(), jwt.getClaimAsString("dev"));
            return perfilDaConta(conta.getTelefone(), pidNum.longValue());
        }
        return validarSessao(pidNum.longValue(), jwt.getClaimAsString("dev"));
    }

    /**
     * Telefone da sessão a partir do token (para listar perfis). Token novo → conta
     * pelo cid; token antigo → sessão do próprio paciente. Valida o aparelho.
     */
    public String telefoneDaSessao(Jwt jwt) {
        Object cid = jwt.getClaim("cid");
        String dev = jwt.getClaimAsString("dev");
        if (cid instanceof Number cidNum) {
            return contaValidaPorId(cidNum.longValue(), dev).getTelefone();
        }
        Object pid = jwt.getClaim("pid");
        if (pid instanceof Number pidNum) {
            return normalizarTelefone(validarSessao(pidNum.longValue(), dev).getTelefone());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
    }

    /**
     * O responsável (cadastro) que a sessão está representando ao agir por um
     * dependente: quando o telefone da conta é responsável do perfil ativo (pid).
     * Vazio quando é o perfil próprio (mesmo telefone) — aí o comentário é do paciente.
     */
    public Optional<Responsavel> responsavelDaSessao(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number pidNum)) {
            return Optional.empty();
        }
        String tel = normalizarTelefone(telefoneDaSessao(jwt)); // valida a sessão e devolve o telefone da conta
        Paciente perfil = repository.findById(pidNum.longValue()).orElse(null);
        if (tel == null || tel.isEmpty() || perfil == null) {
            return Optional.empty();
        }
        if (tel.equals(normalizarTelefone(perfil.getTelefone()))) {
            return Optional.empty(); // perfil próprio: não é "via responsável"
        }
        return responsavelRepository.findFirstByPaciente_IdAndTelefoneOrderByIdAsc(pidNum.longValue(), tel);
    }

    /**
     * Nível de acesso da sessão a uma funcionalidade do app. Perfil PRÓPRIO (a sessão
     * não age por um responsável) tem acesso total; perfil dependente usa as permissões
     * do responsável, com ausência = SEM_ACESSO (padrão).
     */
    public NivelAcessoResponsavel nivelDaSessao(Jwt jwt, FuncionalidadeApp funcionalidade) {
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

    /**
     * Nível da (conta, perfil) numa funcionalidade — para uso fora do fluxo de token,
     * como o WebSocket, cujo principal só carrega cid+pid. Sem conta (token legado) ou
     * perfil próprio → acesso total; dependente → permissões do responsável (ausente = SEM_ACESSO).
     */
    public NivelAcessoResponsavel nivelPorContaEPerfil(Long contaId, Long pacienteId, FuncionalidadeApp funcionalidade) {
        ContaApp conta = contaId == null ? null : contaRepository.findById(contaId).orElse(null);
        Paciente perfil = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (conta == null || perfil == null) {
            return NivelAcessoResponsavel.VISUALIZAR_LANCAR; // legado/sem conta: perfil próprio, sem trava
        }
        String tel = normalizarTelefone(conta.getTelefone());
        if (tel != null && tel.equals(normalizarTelefone(perfil.getTelefone()))) {
            return NivelAcessoResponsavel.VISUALIZAR_LANCAR; // perfil próprio
        }
        return responsavelRepository.findFirstByPaciente_IdAndTelefoneOrderByIdAsc(pacienteId, tel)
                .map(r -> r.getPermissoes().getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO))
                .orElse(NivelAcessoResponsavel.SEM_ACESSO);
    }

    /**
     * Conta da sessão para trocar de perfil. Token novo → conta pelo cid; token
     * antigo → valida a sessão do próprio paciente e MIGRA para uma conta_app
     * (mantendo o mesmo aparelho), para reemitir um token novo com cid.
     */
    public ContaApp contaParaTroca(Jwt jwt) {
        Object cid = jwt.getClaim("cid");
        String dev = jwt.getClaimAsString("dev");
        if (cid instanceof Number cidNum) {
            return contaValidaPorId(cidNum.longValue(), dev);
        }
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number pidNum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        Paciente p = validarSessao(pidNum.longValue(), dev);
        return upsertConta(normalizarTelefone(p.getTelefone()), dev);
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
    public Paciente perfilDaConta(String telefoneConta, Long pacienteId) {
        Paciente paciente = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (paciente == null || !perfilPertenceAConta(telefoneConta, paciente)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Perfil não disponível para esta conta");
        }
        return paciente;
    }

    /** O paciente é o próprio (mesmo telefone) ou um dependente (o telefone é responsável dele). */
    private boolean perfilPertenceAConta(String telefoneConta, Paciente paciente) {
        String telConta = normalizarTelefone(telefoneConta);
        if (telConta == null || telConta.isEmpty()) {
            return false;
        }
        if (telConta.equals(normalizarTelefone(paciente.getTelefone()))) {
            // Perfil próprio: respeita a revogação administrativa (ativo=false → sem acesso).
            return paciente.isAtivo();
        }
        return responsavelRepository.existsByTelefoneAndPaciente_Id(telConta, paciente.getId());
    }

    /**
     * Revoga o acesso: desloga o aparelho atual (o paciente pode reativar por OTP).
     * Encerra tanto a sessão legada (campos do paciente) quanto a nova (conta do
     * telefone) — senão um token com cid seguiria válido em /meu/**.
     */
    public void revogar(Paciente paciente) {
        paciente.setAtivo(false);
        paciente.setDispositivoAtivo(null);
        repository.save(paciente);
        String tel = normalizarTelefone(paciente.getTelefone());
        if (tel != null && !tel.isEmpty()) {
            contaRepository.findByTelefone(tel).ifPresent(conta -> {
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

    /**
     * Valida uma sessão do modelo antigo: paciente ativo e o aparelho é o vinculado.
     * Ainda usada por tokens antigos e pelo WebSocket do chat (perfil próprio).
     */
    public Paciente validarSessao(Long pacienteId, String dispositivoId) {
        Paciente paciente = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (paciente == null || !paciente.isAtivo()
                || dispositivoId == null || !dispositivoId.equals(paciente.getDispositivoAtivo())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return paciente;
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
}
