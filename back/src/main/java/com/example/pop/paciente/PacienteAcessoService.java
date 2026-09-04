package com.example.pop.paciente;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.verificacao.CanalVerificacao;
import com.example.pop.verificacao.VerificacaoService;

/**
 * Acesso do paciente ao app: o próprio paciente pede um código (OTP) por SMS
 * para o seu telefone principal, digita, e a sessão é amarrada a um único
 * aparelho. A verificação do código é delegada ao provedor (Twilio Verify); o
 * backend não gera nem guarda código.
 *
 * <p>O envio por WhatsApp continua suportado no {@link VerificacaoService}, mas
 * está desativado no fluxo até a WhatsApp Business Account ter template de
 * autenticação aprovado — por isso aqui o canal é fixo em SMS.
 */
@Service
public class PacienteAcessoService {

    /** Intervalo mínimo entre dois envios para o mesmo telefone. */
    private static final long COOLDOWN_MS = 60_000L;
    /** Máximo de envios por telefone dentro da janela. */
    private static final int MAX_POR_JANELA = 5;
    private static final long JANELA_MS = 3_600_000L; // 1 hora

    private final PacienteRepository repository;
    private final VerificacaoService verificacao;
    /** Rate-limit por telefone (em memória) para evitar SMS/WhatsApp bombing e abuso de custo. */
    private final Map<String, Deque<Long>> enviosPorTelefone = new ConcurrentHashMap<>();

    public PacienteAcessoService(PacienteRepository repository, VerificacaoService verificacao) {
        this.repository = repository;
        this.verificacao = verificacao;
    }

    /** Normaliza o telefone para apenas dígitos. */
    public static String normalizarTelefone(String telefone) {
        return telefone == null ? null : telefone.replaceAll("\\D", "");
    }

    /**
     * Envia o código de verificação por SMS para o telefone PRINCIPAL do
     * paciente. Telefone não cadastrado → 404 com orientação.
     */
    public void solicitarCodigo(String telefone) {
        Paciente paciente = buscarPorTelefone(telefone);
        if (paciente == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Telefone não encontrado no cadastro. Entre em contato com a sua unidade de saúde.");
        }
        checarLimiteEnvio(paciente.getTelefone());
        verificacao.enviar(e164(paciente.getTelefone()), CanalVerificacao.SMS);
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
     * Confere o código (via provedor), amarra a sessão ao aparelho (invalidando o
     * anterior) e libera o acesso. Lança 401 se telefone/código não conferem.
     */
    public Paciente ativar(String telefone, String codigo, String dispositivoId) {
        Paciente paciente = buscarPorTelefone(telefone);
        boolean aprovado = paciente != null && verificacao.checar(e164(paciente.getTelefone()), codigo);
        if (!aprovado) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telefone ou código inválido");
        }
        paciente.setAtivo(true);
        paciente.setDispositivoAtivo(dispositivoId);
        return repository.save(paciente);
    }

    /** Revoga o acesso: desloga o aparelho atual (o paciente pode reativar por OTP). */
    public void revogar(Paciente paciente) {
        paciente.setAtivo(false);
        paciente.setDispositivoAtivo(null);
        repository.save(paciente);
    }

    private Paciente buscarPorTelefone(String telefone) {
        String tel = normalizarTelefone(telefone);
        return (tel == null || tel.isEmpty()) ? null : repository.findByTelefone(tel).orElse(null);
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

    /**
     * Resolve e valida o paciente logado a partir do token do app: lê o claim
     * {@code pid} e confere sessão (paciente ativo + aparelho vinculado). É a
     * ÚNICA fonte do paciente logado nos endpoints /meu/** — nunca confiar em id
     * vindo do cliente.
     */
    public Paciente pacienteDoToken(Jwt jwt) {
        Object pid = jwt.getClaim("pid");
        if (!(pid instanceof Number numero)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return validarSessao(numero.longValue(), jwt.getClaimAsString("dev"));
    }

    /** Valida uma sessão do app: paciente ativo e o aparelho é o atualmente vinculado. */
    public Paciente validarSessao(Long pacienteId, String dispositivoId) {
        Paciente paciente = pacienteId == null ? null : repository.findById(pacienteId).orElse(null);
        if (paciente == null || !paciente.isAtivo()
                || dispositivoId == null || !dispositivoId.equals(paciente.getDispositivoAtivo())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        return paciente;
    }
}
