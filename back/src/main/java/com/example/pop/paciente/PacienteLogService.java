package com.example.pop.paciente;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Auditoria (LGPD) do cadastro do paciente: registra criação, alteração, inativação e
 * reativação — sempre com quem fez e quando — e lê a linha do tempo.
 *
 * <p>O diff é GRANULAR: cada campo escalar, cada telefone adicional (adicionado/removido)
 * e cada atributo de cada responsável (nome, telefone, uma permissão) viram uma linha
 * própria. Assim, mudar uma única permissão de um responsável gera UMA linha, não o bloco
 * inteiro. Escrita chamada na transação do cadastro: sem log, o cadastro não é confirmado.
 */
@Service
public class PacienteLogService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final PacienteLogRepository repository;
    private final UsuarioRepository usuarioRepository;

    public PacienteLogService(PacienteLogRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Fotografia estrutural do cadastro antes de editar (para o diff granular). */
    public record SnapshotPaciente(
            Map<CampoPaciente, String> escalares,
            List<String> telefonesAdicionais,
            List<ResponsavelSnapshot> responsaveis,
            List<String> unidades) {
    }

    /** Estado de um responsável no momento do snapshot (permissões copiadas, não referenciadas). */
    public record ResponsavelSnapshot(
            Long id,
            String nome,
            String telefone,
            boolean ativo,
            Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
    }

    public SnapshotPaciente snapshot(Paciente p) {
        Map<CampoPaciente, String> escalares = new EnumMap<>(CampoPaciente.class);
        for (CampoPaciente c : CampoPaciente.values()) {
            escalares.put(c, c.valor(p));
        }
        List<String> telefones = p.getTelefonesAdicionais() == null ? List.of()
                : new ArrayList<>(p.getTelefonesAdicionais());
        List<ResponsavelSnapshot> responsaveis = new ArrayList<>();
        if (p.getResponsaveis() != null) {
            for (Responsavel r : p.getResponsaveis()) {
                responsaveis.add(new ResponsavelSnapshot(r.getId(), r.getNome(), r.getTelefone(), r.isAtivo(),
                        copiarPermissoes(r.getPermissoes())));
            }
        }
        List<String> unidades = new ArrayList<>();
        if (p.getUnidades() != null) {
            for (Unidade u : p.getUnidades()) {
                unidades.add(u.getNome());
            }
        }
        return new SnapshotPaciente(escalares, telefones, responsaveis, unidades);
    }

    /** Evento de criação: registra quem cadastrou e tudo que foi preenchido (antes = null). */
    public void registrarCriacao(Paciente novo, Long usuarioId) {
        PacienteLog log = novoEvento(novo, TipoEventoPaciente.CRIACAO, usuarioId);
        List<PacienteLogAlteracao> mudancas = log.getAlteracoes();

        for (CampoPaciente c : CampoPaciente.values()) {
            String valor = c.valor(novo);
            if (valor != null) {
                mudancas.add(alteracao(log, c.name(), c.getDescricao(), null, valor));
            }
        }
        for (String telefone : nn(novo.getTelefonesAdicionais())) {
            mudancas.add(alteracao(log, "TELEFONE_ADICIONAL", "Telefone adicional adicionado", null, telefone));
        }
        for (Responsavel r : nn(novo.getResponsaveis())) {
            mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável adicionado: " + r.getNome(),
                    null, detalhes(r.getNome(), r.getTelefone(), r.getPermissoes())));
        }
        if (novo.getUnidades() != null) {
            for (Unidade u : novo.getUnidades()) {
                mudancas.add(alteracao(log, "UNIDADE", "Unidade de acesso adicionada", null, u.getNome()));
            }
        }
        repository.save(log);
    }

    /**
     * Evento de alteração: compara o snapshot de antes com o estado atual e registra só o
     * que mudou de fato — campo a campo, telefone a telefone e atributo a atributo por
     * responsável. Nada mudou → não grava nada.
     */
    public void registrarAlteracao(SnapshotPaciente antes, Paciente depois, Long usuarioId) {
        PacienteLog log = novoEvento(depois, TipoEventoPaciente.ALTERACAO, usuarioId);
        List<PacienteLogAlteracao> mudancas = new ArrayList<>();

        diffEscalares(log, antes, depois, mudancas);
        diffTelefonesAdicionais(log, antes, depois, mudancas);
        diffResponsaveis(log, antes, depois, mudancas);
        diffUnidades(log, antes, depois, mudancas);

        if (mudancas.isEmpty()) {
            return; // nenhuma mudança real: não polui a linha do tempo
        }
        log.getAlteracoes().addAll(mudancas);
        repository.save(log);
    }

    /** Evento de inativação do cadastro (soft-delete). */
    public void registrarInativacao(Paciente p, Long usuarioId) {
        repository.save(novoEvento(p, TipoEventoPaciente.INATIVACAO, usuarioId));
    }

    /** Evento de reativação do cadastro. */
    public void registrarReativacao(Paciente p, Long usuarioId) {
        repository.save(novoEvento(p, TipoEventoPaciente.REATIVACAO, usuarioId));
    }

    /** Linha do tempo de auditoria do paciente (mais antigo primeiro). */
    @Transactional(readOnly = true)
    public List<PacienteLogResponse> listar(Long pacienteId) {
        return repository.findByPacienteIdOrderByCriadoEmAscIdAsc(pacienteId).stream()
                .map(PacienteLogResponse::from)
                .toList();
    }

    // --- diffs ---------------------------------------------------------------

    private void diffEscalares(PacienteLog log, SnapshotPaciente antes, Paciente depois,
            List<PacienteLogAlteracao> mudancas) {
        for (CampoPaciente c : CampoPaciente.values()) {
            String valorAntes = antes.escalares().get(c);
            String valorDepois = c.valor(depois);
            if (!Objects.equals(valorAntes, valorDepois)) {
                mudancas.add(alteracao(log, c.name(), c.getDescricao(), valorAntes, valorDepois));
            }
        }
    }

    private void diffTelefonesAdicionais(PacienteLog log, SnapshotPaciente antes, Paciente depois,
            List<PacienteLogAlteracao> mudancas) {
        Set<String> antesTels = new LinkedHashSet<>(antes.telefonesAdicionais());
        Set<String> depoisTels = new LinkedHashSet<>(nn(depois.getTelefonesAdicionais()));
        for (String tel : depoisTels) {
            if (!antesTels.contains(tel)) {
                mudancas.add(alteracao(log, "TELEFONE_ADICIONAL", "Telefone adicional adicionado", null, tel));
            }
        }
        for (String tel : antesTels) {
            if (!depoisTels.contains(tel)) {
                mudancas.add(alteracao(log, "TELEFONE_ADICIONAL", "Telefone adicional removido", tel, null));
            }
        }
    }

    private void diffUnidades(PacienteLog log, SnapshotPaciente antes, Paciente depois,
            List<PacienteLogAlteracao> mudancas) {
        Set<String> antesU = new LinkedHashSet<>(antes.unidades());
        Set<String> depoisU = new LinkedHashSet<>();
        if (depois.getUnidades() != null) {
            for (Unidade u : depois.getUnidades()) {
                depoisU.add(u.getNome());
            }
        }
        for (String u : depoisU) {
            if (!antesU.contains(u)) {
                mudancas.add(alteracao(log, "UNIDADE", "Unidade de acesso adicionada", null, u));
            }
        }
        for (String u : antesU) {
            if (!depoisU.contains(u)) {
                mudancas.add(alteracao(log, "UNIDADE", "Unidade de acesso removida", u, null));
            }
        }
    }

    private void diffResponsaveis(PacienteLog log, SnapshotPaciente antes, Paciente depois,
            List<PacienteLogAlteracao> mudancas) {
        Map<Long, ResponsavelSnapshot> antesPorId = new java.util.HashMap<>();
        for (ResponsavelSnapshot r : antes.responsaveis()) {
            if (r.id() != null) {
                antesPorId.put(r.id(), r);
            }
        }
        Set<Long> depoisIds = new LinkedHashSet<>();

        for (Responsavel r : nn(depois.getResponsaveis())) {
            if (r.getId() != null) {
                depoisIds.add(r.getId());
            }
            ResponsavelSnapshot base = r.getId() == null ? null : antesPorId.get(r.getId());
            if (base == null) {
                // Responsável novo (id ausente no snapshot): resumo em uma linha.
                mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável adicionado: " + r.getNome(),
                        null, detalhes(r.getNome(), r.getTelefone(), r.getPermissoes())));
                continue;
            }
            diffUmResponsavel(log, base, r, mudancas);
        }

        for (ResponsavelSnapshot base : antes.responsaveis()) {
            if (base.id() != null && !depoisIds.contains(base.id())) {
                mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável removido: " + base.nome(),
                        detalhes(base.nome(), base.telefone(), base.permissoes()), null));
            }
        }
    }

    /** Diff de um responsável existente: nome, telefone e cada permissão que mudou. */
    private void diffUmResponsavel(PacienteLog log, ResponsavelSnapshot antes, Responsavel depois,
            List<PacienteLogAlteracao> mudancas) {
        String nome = depois.getNome();
        if (!Objects.equals(antes.nome(), depois.getNome())) {
            mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável " + nome + " — Nome",
                    antes.nome(), depois.getNome()));
        }
        if (!Objects.equals(vazioParaNulo(antes.telefone()), vazioParaNulo(depois.getTelefone()))) {
            mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável " + nome + " — Telefone",
                    vazioParaNulo(antes.telefone()), vazioParaNulo(depois.getTelefone())));
        }
        if (antes.ativo() != depois.isAtivo()) {
            mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável " + nome + " — Situação",
                    antes.ativo() ? "ativo" : "inativo", depois.isAtivo() ? "ativo" : "inativo"));
        }
        Map<FuncionalidadeApp, NivelAcessoResponsavel> permAntes = antes.permissoes();
        Map<FuncionalidadeApp, NivelAcessoResponsavel> permDepois = copiarPermissoes(depois.getPermissoes());
        for (FuncionalidadeApp f : FuncionalidadeApp.values()) {
            NivelAcessoResponsavel na = permAntes.getOrDefault(f, NivelAcessoResponsavel.SEM_ACESSO);
            NivelAcessoResponsavel nd = permDepois.getOrDefault(f, NivelAcessoResponsavel.SEM_ACESSO);
            if (na != nd) {
                mudancas.add(alteracao(log, "RESPONSAVEL", "Responsável " + nome + " — " + funcLabel(f),
                        nivelLabel(na), nivelLabel(nd)));
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private PacienteLog novoEvento(Paciente paciente, TipoEventoPaciente tipo, Long usuarioId) {
        PacienteLog log = new PacienteLog();
        log.setPaciente(paciente);
        log.setTipo(tipo);
        log.setCriadoEm(LocalDateTime.now(FUSO));
        // findById (e não getReference): ator inexistente não estoura FK; fica "Sistema".
        Usuario usuario = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        log.setUsuario(usuario);
        log.setAutor(usuario != null ? AutorLogPaciente.UNIDADE : AutorLogPaciente.SISTEMA);
        return log;
    }

    private PacienteLogAlteracao alteracao(PacienteLog log, String campo, String rotulo, String antes, String depois) {
        PacienteLogAlteracao a = new PacienteLogAlteracao();
        a.setLog(log);
        a.setCampo(campo);
        a.setRotulo(rotulo);
        a.setValorAntes(antes);
        a.setValorDepois(depois);
        return a;
    }

    private static Map<FuncionalidadeApp, NivelAcessoResponsavel> copiarPermissoes(
            Map<FuncionalidadeApp, NivelAcessoResponsavel> origem) {
        Map<FuncionalidadeApp, NivelAcessoResponsavel> copia = new EnumMap<>(FuncionalidadeApp.class);
        if (origem != null) {
            copia.putAll(origem);
        }
        return copia;
    }

    /** Resumo legível de um responsável para add/remover: "nome (telefone) — permissões". */
    private static String detalhes(String nome, String telefone,
            Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
        String tel = (telefone == null || telefone.isBlank()) ? "sem telefone" : telefone;
        String perms = permissoesResumo(permissoes);
        return nome + " (" + tel + ")" + (perms.isEmpty() ? "" : " — " + perms);
    }

    /** "Chat: visualizar e lançar; Prontuário: só visualizar" — só as funcionalidades concedidas. */
    private static String permissoesResumo(Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
        if (permissoes == null || permissoes.isEmpty()) {
            return "";
        }
        return permissoes.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() != NivelAcessoResponsavel.SEM_ACESSO)
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .map(e -> funcLabel(e.getKey()) + ": " + nivelLabel(e.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String funcLabel(FuncionalidadeApp f) {
        return switch (f) {
            case AGENDAMENTOS -> "Agendamentos";
            case CHAT -> "Chat";
            case SAU -> "SAU";
            case REDE_SOCIAL -> "Rede social";
            case MEU_PERFIL -> "Meu perfil";
            case PRONTUARIO -> "Prontuário";
            case NPS -> "NPS";
        };
    }

    private static String nivelLabel(NivelAcessoResponsavel n) {
        return switch (n) {
            case SEM_ACESSO -> "sem acesso";
            case VISUALIZAR -> "só visualizar";
            case VISUALIZAR_LANCAR -> "visualizar e lançar";
        };
    }

    private static String vazioParaNulo(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static <T> List<T> nn(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private static Set<Responsavel> nn(Set<Responsavel> conjunto) {
        return conjunto == null ? Set.of() : conjunto;
    }
}
