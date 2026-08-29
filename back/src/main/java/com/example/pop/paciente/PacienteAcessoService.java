package com.example.pop.paciente;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de acesso do paciente ao app: geração/validação do código de ativação,
 * liberação global e vínculo com um único aparelho por vez.
 */
@Service
public class PacienteAcessoService {

    /** Validade do código de ativação (horas). */
    private static final int VALIDADE_HORAS = 48;

    private final PacienteRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    /** Hash de referência (mesmo custo) para igualar o tempo quando o telefone não existe. */
    private final String hashFicticio;

    public PacienteAcessoService(PacienteRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode("timing-guard-nao-usar");
    }

    /** Normaliza o telefone para apenas dígitos. */
    public static String normalizarTelefone(String telefone) {
        return telefone == null ? null : telefone.replaceAll("\\D", "");
    }

    /**
     * Libera o paciente (global) e gera um novo código de ativação de 6 dígitos.
     * Retorna o código em texto puro (mostrado uma única vez ao admin).
     */
    public String gerarCodigo(Paciente paciente) {
        String codigo = String.format("%06d", random.nextInt(1_000_000));
        paciente.setCodigoAtivacaoHash(passwordEncoder.encode(codigo));
        paciente.setCodigoAtivacaoExpiraEm(LocalDateTime.now().plusHours(VALIDADE_HORAS));
        paciente.setAtivo(true);
        repository.save(paciente);
        return codigo;
    }

    /** Revoga o acesso: desativa e limpa código e aparelho ativo. */
    public void revogar(Paciente paciente) {
        paciente.setAtivo(false);
        paciente.setCodigoAtivacaoHash(null);
        paciente.setCodigoAtivacaoExpiraEm(null);
        paciente.setDispositivoAtivo(null);
        repository.save(paciente);
    }

    /**
     * Valida telefone + código e amarra a sessão ao aparelho (invalidando o anterior).
     * O código é de uso único. Lança 401 se algo não confere.
     */
    public Paciente ativar(String telefone, String codigo, String dispositivoId) {
        String tel = normalizarTelefone(telefone);
        Paciente paciente = (tel == null || tel.isEmpty()) ? null : repository.findByTelefone(tel).orElse(null);
        String hash = (paciente != null && paciente.getCodigoAtivacaoHash() != null)
                ? paciente.getCodigoAtivacaoHash()
                : hashFicticio;
        // Sempre compara (contra hash fictício quando não existe) para não vazar, pelo tempo, se o telefone existe.
        boolean codigoConfere = passwordEncoder.matches(codigo == null ? "" : codigo.trim(), hash);

        boolean valido = paciente != null
                && paciente.isAtivo()
                && paciente.getCodigoAtivacaoHash() != null
                && paciente.getCodigoAtivacaoExpiraEm() != null
                && paciente.getCodigoAtivacaoExpiraEm().isAfter(LocalDateTime.now())
                && codigoConfere;
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telefone ou código inválido");
        }

        paciente.setDispositivoAtivo(dispositivoId);
        // Código é de uso único.
        paciente.setCodigoAtivacaoHash(null);
        paciente.setCodigoAtivacaoExpiraEm(null);
        return repository.save(paciente);
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
