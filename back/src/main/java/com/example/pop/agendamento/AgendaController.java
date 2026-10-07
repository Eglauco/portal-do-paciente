package com.example.pop.agendamento;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

/**
 * Tela "Agendas" (back-office): o SLOT do profissional (dia + especialidade/procedimento/unidade). Os pacientes
 * marcados ficam nos {@link Horario} (tela/endpoints {@code /horario}). Escopada pela unidade ativa.
 */
@RestController
@RequestMapping("/agenda")
public class AgendaController {

    private static final int TAMANHO_MAXIMO = 100;
    private static final Sort ORDEM = Sort.by(Sort.Direction.DESC, "data").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AgendaRepository repository;
    private final HorarioRepository horarioRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final ProcedimentoRepository procedimentoRepository;
    private final UnidadeRepository unidadeRepository;

    public AgendaController(AgendaRepository repository, HorarioRepository horarioRepository,
            EspecialidadeRepository especialidadeRepository, ProfissionalSaudeRepository profissionalRepository,
            ProcedimentoRepository procedimentoRepository, UnidadeRepository unidadeRepository) {
        this.repository = repository;
        this.horarioRepository = horarioRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.unidadeRepository = unidadeRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Pagina<AgendaResumoResponse> listar(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) String profissionalNome,
            @RequestParam(required = false) String especialidadeNome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, ORDEM);
        Page<Agenda> resultado = repository.search(unidadeId, data, padrao(profissionalNome), padrao(especialidadeNome),
                pageable);
        List<Long> ids = resultado.getContent().stream().map(Agenda::getId).toList();
        Map<Long, Long> contagens = ids.isEmpty() ? Map.of()
                : horarioRepository.contarPorAgendas(ids).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        List<AgendaResumoResponse> content = resultado.getContent().stream()
                .map(a -> AgendaResumoResponse.from(a, contagens.getOrDefault(a.getId(), 0L)))
                .toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe da agenda: o slot + os horários (pacientes marcados). */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AgendaResponse detalhar(@PathVariable Long id) {
        Agenda a = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agenda não encontrada."));
        return AgendaResponse.from(a, horarioRepository.findByAgenda_IdOrderByDataHoraAsc(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaResponse criar(@Valid @RequestBody AgendaRequest request) {
        Agenda a = new Agenda();
        aplicar(a, request);
        return AgendaResponse.from(repository.save(a), List.of());
    }

    @PutMapping("/{id}")
    @Transactional
    public AgendaResponse atualizar(@PathVariable Long id, @Valid @RequestBody AgendaRequest request) {
        Agenda a = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agenda não encontrada."));
        aplicar(a, request);
        repository.save(a);
        return AgendaResponse.from(a, horarioRepository.findByAgenda_IdOrderByDataHoraAsc(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agenda não encontrada.");
        }
        if (horarioRepository.countByAgenda_Id(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Agenda com horários marcados não pode ser excluída. Remova os horários primeiro.");
        }
        repository.deleteById(id);
    }

    private void aplicar(Agenda a, AgendaRequest request) {
        a.setData(request.data());
        // Nome é só um rótulo editável; o código de integração (CROSS) NÃO é tocado aqui — fica a cargo do SIRESP.
        String nome = request.nome() == null ? null : request.nome().trim();
        a.setNome(nome == null || nome.isBlank() ? null : nome);
        a.setEspecialidade(especialidadeRepository.findById(request.especialidadeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Especialidade não encontrada")));
        a.setProfissionalSaude(profissionalRepository.findById(request.profissionalSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional não encontrado")));
        a.setProcedimento(procedimentoRepository.findById(request.procedimentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Procedimento não encontrado")));
        a.setUnidadeSaude(unidadeRepository.findById(request.unidadeSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade não encontrada")));
    }

    private static String padrao(String nome) {
        return nome == null || nome.isBlank() ? null : "%" + nome.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
