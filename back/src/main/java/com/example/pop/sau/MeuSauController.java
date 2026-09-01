package com.example.pop.sau;

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
import com.example.pop.common.Ref;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

/** SAU do lado do paciente (app): abre e acompanha as próprias manifestações. */
@RestController
@RequestMapping("/meu/sau")
public class MeuSauController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ManifestacaoRepository repository;
    private final SauService sauService;
    private final PacienteAcessoService acessoService;
    private final UnidadeRepository unidadeRepository;
    private final TipoManifestacaoRepository tipoRepository;

    public MeuSauController(ManifestacaoRepository repository, SauService sauService,
            PacienteAcessoService acessoService, UnidadeRepository unidadeRepository,
            TipoManifestacaoRepository tipoRepository) {
        this.repository = repository;
        this.sauService = sauService;
        this.acessoService = acessoService;
        this.unidadeRepository = unidadeRepository;
        this.tipoRepository = tipoRepository;
    }

    /** Unidades disponíveis para o paciente escolher ao abrir a manifestação. */
    @GetMapping("/unidades")
    public List<Ref> unidades() {
        return unidadeRepository.findAll(Sort.by("nome")).stream()
                .map(u -> new Ref(u.getId(), u.getNome())).toList();
    }

    /** Tipos ATIVOS para o paciente escolher ao abrir a manifestação. */
    @GetMapping("/tipos")
    public List<TipoManifestacaoResponse> tipos() {
        return tipoRepository.findByAtivoTrueOrderByNome().stream()
                .map(TipoManifestacaoResponse::from).toList();
    }

    /** Manifestações do paciente logado (mais recentes primeiro). */
    @GetMapping
    public Pagina<ManifestacaoResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO));
        Page<Manifestacao> resultado = repository.findByPacienteIdOrderByAtualizadoEmDesc(pacienteId, pageable);
        List<ManifestacaoResponse> content = resultado.getContent().stream().map(sauService::toResponse).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Abre uma manifestação (tipo + unidade + primeira mensagem). */
    @PostMapping
    @Transactional
    public ManifestacaoDetalheResponse abrir(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AbrirManifestacaoRequest request) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        Manifestacao m = sauService.abrir(paciente, request.unidadeId(), request.tipoId(), request.texto());
        return sauService.toDetalhe(m, false);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ManifestacaoDetalheResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return sauService.toDetalhe(minha(jwt, id), false);
    }

    /** Paciente responde na thread (reabre se estava fechada). */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ManifestacaoDetalheResponse responder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MensagemSauRequest request) {
        Manifestacao m = sauService.responderComoPaciente(minha(jwt, id), request.texto());
        return sauService.toDetalhe(m, false);
    }

    /** Carrega a manifestação garantindo que é do paciente logado (404 caso contrário). */
    private Manifestacao minha(Jwt jwt, Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return repository.findByIdAndPacienteId(id, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manifestação não encontrada"));
    }
}
