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
public class AgendamentoLogService {

    private final AgendamentoLogRepository repository;
    private final PacienteRepository pacienteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final UsuarioRepository usuarioRepository;

    public AgendamentoLogService(AgendamentoLogRepository repository, PacienteRepository pacienteRepository,
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
    public void registrarDoApp(Agendamento ag, StatusAgendamento antes, StatusAgendamento depois,
            Responsavel responsavel) {
        if (responsavel != null) {
            gravar(ag, antes, depois, AutorLogAgendamento.RESPONSAVEL, null, responsavel.getId(), null);
        } else {
            gravar(ag, antes, depois, AutorLogAgendamento.PACIENTE, ag.getPaciente().getId(), null, null);
        }
    }

    /** Troca feita pela unidade (back-office); identifica o atendente pelo uid do token. */
    public void registrarDaUnidade(Agendamento ag, StatusAgendamento antes, StatusAgendamento depois, Long usuarioId) {
        gravar(ag, antes, depois, AutorLogAgendamento.UNIDADE, null, null, usuarioId);
    }

    private void gravar(Agendamento ag, StatusAgendamento antes, StatusAgendamento depois,
            AutorLogAgendamento autor, Long pacienteId, Long responsavelId, Long usuarioId) {
        if (depois == null || antes == depois) {
            return; // sem troca real de status: nada a registrar
        }
        AgendamentoLog log = new AgendamentoLog();
        log.setAgendamento(ag);
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

    /** Linha do tempo das trocas de status do agendamento (mais antigo primeiro). */
    public List<AgendamentoLogResponse> listar(Long agendamentoId) {
        return repository.findByAgendamentoIdOrderByCriadoEmAsc(agendamentoId).stream()
                .map(AgendamentoLogResponse::from).toList();
    }
}
