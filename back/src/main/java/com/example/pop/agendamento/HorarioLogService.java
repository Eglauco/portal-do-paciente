package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.usuario.UsuarioRepository;

/** Auditoria do agendamento: grava cada troca de status (quem fez) e lê a linha do tempo. */
@Service
public class HorarioLogService {

    private final HorarioLogRepository repository;
    private final PacienteRepository pacienteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final UsuarioRepository usuarioRepository;

    public HorarioLogService(HorarioLogRepository repository, PacienteRepository pacienteRepository,
            ResponsavelRepository responsavelRepository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.responsavelRepository = responsavelRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Troca feita pelo app: por um responsável (quando a sessão age por um perfil
     * dependente) ou pelo próprio paciente. Só grava quando o status mudou de fato.
     */
    public void registrarDoApp(Horario ag, StatusAgendamento antes, StatusAgendamento depois,
            Responsavel responsavel) {
        if (responsavel != null) {
            gravar(ag, antes, depois, AutorLogHorario.RESPONSAVEL, null, responsavel.getId(), null);
        } else {
            gravar(ag, antes, depois, AutorLogHorario.PACIENTE, ag.getPaciente().getId(), null, null);
        }
    }

    /** Troca feita pela unidade (back-office); identifica o atendente pelo uid do token. */
    public void registrarDaUnidade(Horario ag, StatusAgendamento antes, StatusAgendamento depois, Long usuarioId) {
        gravar(ag, antes, depois, AutorLogHorario.UNIDADE, null, null, usuarioId);
    }

    /** Troca feita por uma rotina automática do sistema (sem ator humano) — ex.: confirmação vencida. */
    public void registrarDoSistema(Horario ag, StatusAgendamento antes, StatusAgendamento depois) {
        gravar(ag, antes, depois, AutorLogHorario.SISTEMA, null, null, null);
    }

    private void gravar(Horario ag, StatusAgendamento antes, StatusAgendamento depois,
            AutorLogHorario autor, Long pacienteId, Long responsavelId, Long usuarioId) {
        if (depois == null || antes == depois) {
            return; // sem troca real de status: nada a registrar
        }
        HorarioLog log = new HorarioLog();
        log.setHorario(ag);
        log.setAutor(autor);
        // findById (e não getReference): se o ator não existir mais, o log fica com ator
        // nulo em vez de estourar a FK e derrubar a operação legítima. Auditoria não pode
        // quebrar a ação.
        if (pacienteId != null) {
            pacienteRepository.findById(pacienteId).ifPresent(log::setPaciente);
        }
        if (responsavelId != null) {
            responsavelRepository.findById(responsavelId).ifPresent(log::setResponsavel);
        }
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(log::setUsuario);
        }
        log.setStatusAnterior(antes);
        log.setStatusNovo(depois);
        log.setCriadoEm(LocalDateTime.now());
        repository.save(log);
    }

    /**
     * Evento do back-office que não é troca de status (ex.: convite do app enviado por SMS), feito pelo
     * atendente {@code usuarioId}. {@code detalhe} vai para exibição — nunca gravar o telefone inteiro (LGPD).
     */
    public void registrarEvento(Horario ag, EventoLogHorario evento, String detalhe, Long usuarioId) {
        HorarioLog log = new HorarioLog();
        log.setHorario(ag);
        log.setAutor(AutorLogHorario.UNIDADE);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(log::setUsuario);
        }
        log.setEvento(evento);
        log.setDetalhe(detalhe);
        log.setCriadoEm(LocalDateTime.now());
        repository.save(log);
    }

    /** Linha do tempo COMPLETA do agendamento — status e eventos (mais antigo primeiro). Back-office. */
    public List<HorarioLogResponse> listar(Long agendamentoId) {
        return repository.findByHorarioIdOrderByCriadoEmAsc(agendamentoId).stream()
                .map(HorarioLogResponse::from).toList();
    }

    /**
     * Só as trocas de STATUS (sem os eventos internos do back-office, como o convite do app) — para o app do
     * paciente e telas antigas que só sabem desenhar transições de status.
     */
    public List<HorarioLogResponse> listarStatus(Long agendamentoId) {
        return repository.findByHorarioIdOrderByCriadoEmAsc(agendamentoId).stream()
                .filter(l -> l.getEvento() == null)
                .map(HorarioLogResponse::from).toList();
    }
}
