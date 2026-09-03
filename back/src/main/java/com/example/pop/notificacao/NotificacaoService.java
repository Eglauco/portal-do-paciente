package com.example.pop.notificacao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;

/**
 * Guarda e lê as notificações do paciente. A GRAVAÇÃO é best-effort e roda em
 * transação própria (REQUIRES_NEW, erros engolidos): notificar nunca pode quebrar
 * nem sofrer rollback com o fluxo de negócio que a originou (igual ao push).
 */
@Service
public class NotificacaoService {

    private static final int TAMANHO_MAXIMO = 100;

    private final NotificacaoRepository repository;
    private final PacienteRepository pacienteRepository;

    public NotificacaoService(NotificacaoRepository repository, PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
    }

    /** Grava a notificação de um paciente (não quebra o fluxo chamador). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long pacienteId, TipoNotificacao tipo, String titulo, String corpo, Long referenciaId) {
        if (pacienteId == null) {
            return;
        }
        try {
            Paciente paciente = pacienteRepository.findById(pacienteId).orElse(null);
            if (paciente == null) {
                return;
            }
            repository.save(montar(paciente, tipo, titulo, corpo, referenciaId));
        } catch (RuntimeException e) {
            // best-effort: engole para não afetar o fluxo de negócio
        }
    }

    /** Grava a mesma notificação para TODOS os pacientes (ex.: nova publicação). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarParaTodos(TipoNotificacao tipo, String titulo, String corpo, Long referenciaId) {
        try {
            List<Notificacao> lote = pacienteRepository.findAll().stream()
                    .map(p -> montar(p, tipo, titulo, corpo, referenciaId))
                    .toList();
            repository.saveAll(lote);
        } catch (RuntimeException e) {
            // best-effort
        }
    }

    /**
     * Grava a notificação na transação do CHAMADOR (não best-effort): usado pelo job
     * de lembretes, onde a notificação é o objetivo — se falhar, o disparo é revertido
     * e tenta de novo na próxima execução.
     */
    @Transactional
    public void registrarParaPaciente(Paciente paciente, TipoNotificacao tipo, String titulo, String corpo, Long referenciaId) {
        repository.save(montar(paciente, tipo, titulo, corpo, referenciaId));
    }

    private Notificacao montar(Paciente paciente, TipoNotificacao tipo, String titulo, String corpo, Long referenciaId) {
        Notificacao n = new Notificacao();
        n.setPaciente(paciente);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setCorpo(corpo);
        n.setReferenciaId(referenciaId);
        n.setLida(false);
        n.setCriadoEm(LocalDateTime.now());
        return n;
    }

    @Transactional(readOnly = true)
    public Pagina<NotificacaoResponse> listar(Long pacienteId, int page, int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        Pageable pageable = PageRequest.of(pagina, tamanho);
        Page<Notificacao> resultado = repository.findByPacienteIdOrderByCriadoEmDesc(pacienteId, pageable);
        List<NotificacaoResponse> content = resultado.getContent().stream().map(NotificacaoResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long pacienteId) {
        return repository.countByPacienteIdAndLidaFalse(pacienteId);
    }

    /** Marca como lida ao tocar (idempotente); ignora se não for do paciente. */
    @Transactional
    public void marcarLida(Long id, Long pacienteId) {
        repository.findByIdAndPacienteId(id, pacienteId).ifPresent(n -> {
            if (!n.isLida()) {
                n.setLida(true);
                n.setLidaEm(LocalDateTime.now());
                repository.save(n);
            }
        });
    }
}
