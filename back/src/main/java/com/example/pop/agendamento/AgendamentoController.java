package com.example.pop.agendamento;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.nps.NpsService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/agendamento")
public class AgendamentoController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final AgendamentoRepository repository;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final ProcedimentoRepository procedimentoRepository;
    private final NpsService npsService;

    public AgendamentoController(AgendamentoRepository repository, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, EspecialidadeRepository especialidadeRepository,
            ProfissionalSaudeRepository profissionalRepository, ProcedimentoRepository procedimentoRepository,
            NpsService npsService) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.npsService = npsService;
    }

    /**
     * Lista agendamentos de forma paginada (mais recentes primeiro), com filtros
     * opcionais por status e paciente. Tamanho de página limitado a {@value #TAMANHO_MAXIMO}.
     */
    @GetMapping
    public Pagina<AgendamentoResponse> listar(
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataHora"));
        Page<Agendamento> resultado = repository.search(status, pacienteId, pageable);
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

    @GetMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(a -> ResponseEntity.ok(AgendamentoResponse.from(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResponse criar(@Valid @RequestBody AgendamentoRequest request) {
        Agendamento agendamento = new Agendamento();
        aplicar(agendamento, request);
        // Regra de negócio: todo novo agendamento nasce aguardando confirmação do paciente.
        agendamento.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        return AgendamentoResponse.from(repository.save(agendamento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody AgendamentoRequest request) {
        return repository.findById(id)
                .map(agendamento -> {
                    aplicar(agendamento, request);
                    if (request.statusAgendamento() != null) {
                        agendamento.setStatusAgendamento(request.statusAgendamento());
                    }
                    Agendamento salvo = repository.save(agendamento);
                    // Regra: ao registrar a presença do paciente, gera o NPS vinculado ao atendimento.
                    npsService.gerarSeNecessario(salvo);
                    return ResponseEntity.ok(AgendamentoResponse.from(salvo));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Confirmação do paciente (app): move o status para PACIENTE_CONFIRMOU. */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<AgendamentoResponse> confirmar(@PathVariable Long id) {
        return alterarStatus(id, StatusAgendamento.PACIENTE_CONFIRMOU);
    }

    /** Cancelamento pelo paciente (app): move o status para CANCELADO_PELO_PACIENTE. */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(@PathVariable Long id) {
        return alterarStatus(id, StatusAgendamento.CANCELADO_PELO_PACIENTE);
    }

    private ResponseEntity<AgendamentoResponse> alterarStatus(Long id, StatusAgendamento status) {
        return repository.findById(id)
                .map(agendamento -> {
                    agendamento.setStatusAgendamento(status);
                    return ResponseEntity.ok(AgendamentoResponse.from(repository.save(agendamento)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void aplicar(Agendamento agendamento, AgendamentoRequest request) {
        agendamento.setDataHora(request.dataHora());
        agendamento.setEspecialidade(especialidadeRepository.findById(request.especialidadeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Especialidade não encontrada")));
        agendamento.setProfissionalSaude(profissionalRepository.findById(request.profissionalSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional não encontrado")));
        agendamento.setProcedimento(procedimentoRepository.findById(request.procedimentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Procedimento não encontrado")));
        agendamento.setPaciente(pacienteRepository.findById(request.pacienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado")));
        agendamento.setUnidadeSaude(unidadeRepository.findById(request.unidadeSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade não encontrada")));
    }
}
