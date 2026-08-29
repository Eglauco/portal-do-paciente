package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.motivofalta.MotivoFalta;
import com.example.pop.motivofalta.MotivoFaltaRepository;
import com.example.pop.paciente.PacienteAcessoService;

import jakarta.validation.Valid;

/**
 * Agendamentos do paciente logado (app). O paciente é sempre derivado do token
 * ({@link PacienteAcessoService#pacienteDoToken}); as ações validam a posse e
 * respondem 404 quando o agendamento não é do paciente (não vaza existência).
 */
@RestController
@RequestMapping("/meu/agendamentos")
public class MeusAgendamentosController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final AgendamentoRepository repository;
    private final MotivoFaltaRepository motivoFaltaRepository;
    private final PacienteAcessoService acessoService;

    public MeusAgendamentosController(AgendamentoRepository repository,
            MotivoFaltaRepository motivoFaltaRepository, PacienteAcessoService acessoService) {
        this.repository = repository;
        this.motivoFaltaRepository = motivoFaltaRepository;
        this.acessoService = acessoService;
    }

    /** Lista os agendamentos do paciente logado (mais recentes primeiro). */
    @GetMapping
    public Pagina<AgendamentoResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataHora"));
        Page<Agendamento> resultado = repository.search(null, pacienteId, null, pageable);
        List<AgendamentoResponse> content = resultado.getContent().stream()
                .map(AgendamentoResponse::from)
                .toList();

        return new Pagina<>(
                content,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /** Confirmação do paciente: move o status para PACIENTE_CONFIRMOU. */
    @PostMapping("/{id}/confirmar")
    public AgendamentoResponse confirmar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return alterarStatus(jwt, id, StatusAgendamento.PACIENTE_CONFIRMOU);
    }

    /** Cancelamento pelo paciente: move o status para CANCELADO_PELO_PACIENTE. */
    @PostMapping("/{id}/cancelar")
    public AgendamentoResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return alterarStatus(jwt, id, StatusAgendamento.CANCELADO_PELO_PACIENTE);
    }

    /**
     * Justificativa da falta pelo paciente: registra motivos e texto livre.
     * Só permitido quando o agendamento está em FALTA_PACIENTE.
     */
    @PostMapping("/{id}/justificar-falta")
    @Transactional
    public AgendamentoResponse justificarFalta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody JustificarFaltaRequest request) {
        Agendamento agendamento = meuAgendamento(jwt, id);
        if (agendamento.getStatusAgendamento() != StatusAgendamento.FALTA_PACIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O agendamento não está marcado como falta do paciente");
        }
        List<MotivoFalta> motivos = motivoFaltaRepository.findAllById(request.motivoIds());
        agendamento.setMotivosFalta(motivos);
        String texto = request.justificativa() == null ? null : request.justificativa().trim();
        agendamento.setJustificativaFalta(texto == null || texto.isBlank() ? null : texto);
        agendamento.setFaltaJustificadaEm(LocalDateTime.now());
        return AgendamentoResponse.from(repository.save(agendamento));
    }

    private AgendamentoResponse alterarStatus(Jwt jwt, Long id, StatusAgendamento status) {
        Agendamento agendamento = meuAgendamento(jwt, id);
        agendamento.setStatusAgendamento(status);
        return AgendamentoResponse.from(repository.save(agendamento));
    }

    /** Carrega o agendamento garantindo que é do paciente logado (404 caso contrário). */
    private Agendamento meuAgendamento(Jwt jwt, Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return repository.findByIdAndPacienteId(id, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
    }
}
