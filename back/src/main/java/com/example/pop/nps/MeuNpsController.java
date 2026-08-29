package com.example.pop.nps;

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
import com.example.pop.paciente.PacienteAcessoService;

import jakarta.validation.Valid;

/**
 * NPS do paciente logado (app). Lista/detalhe/resposta sempre escopados ao
 * paciente do token (via agendamento.paciente); id de outro paciente -> 404.
 */
@RestController
@RequestMapping("/meu/nps")
public class MeuNpsController {

    private static final int TAMANHO_MAXIMO = 100;

    private final NpsRepository repository;
    private final NpsService npsService;
    private final PacienteAcessoService acessoService;

    public MeuNpsController(NpsRepository repository, NpsService npsService, PacienteAcessoService acessoService) {
        this.repository = repository;
        this.npsService = npsService;
        this.acessoService = acessoService;
    }

    /** Lista os NPS do paciente logado (opcionalmente filtrando por status, ex.: PENDENTE). */
    @GetMapping
    public Pagina<NpsResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) StatusNps status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<Nps> resultado = repository.search(status, pacienteId, null, pageable);
        List<NpsResponse> content = resultado.getContent().stream().map(NpsResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe de um NPS do paciente logado. */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public NpsDetalheResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return NpsDetalheResponse.from(meuNps(jwt, id));
    }

    /** Resposta do paciente logado (uma nota 0 a 10 por categoria + observação). */
    @PostMapping("/{id}/responder")
    @Transactional
    public NpsDetalheResponse responder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ResponderNpsRequest request) {
        Nps nps = meuNps(jwt, id);
        return NpsDetalheResponse.from(npsService.responder(nps, request));
    }

    /** Carrega o NPS garantindo que é do paciente logado (404 caso contrário). */
    private Nps meuNps(Jwt jwt, Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return repository.findByIdAndAgendamento_Paciente_Id(id, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPS não encontrado"));
    }
}
