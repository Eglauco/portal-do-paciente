package com.example.pop.sau;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import jakarta.validation.Valid;

/** SAU do lado do admin (back-office): lista, vê, responde e fecha manifestações. */
@RestController
@RequestMapping("/sau")
public class SauController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ManifestacaoRepository repository;
    private final SauService sauService;

    public SauController(ManifestacaoRepository repository, SauService sauService) {
        this.repository = repository;
        this.sauService = sauService;
    }

    /** Lista com filtros opcionais de unidade, tipo e status (mais recentes primeiro). */
    @GetMapping
    public Pagina<ManifestacaoResponse> listar(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long tipoId,
            @RequestParam(required = false) StatusManifestacao status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO),
                Sort.by(Sort.Direction.DESC, "atualizadoEm"));
        Page<Manifestacao> resultado = repository.search(unidadeId, tipoId, status, pageable);
        List<ManifestacaoResponse> content = resultado.getContent().stream().map(sauService::toResponse).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ManifestacaoDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(m -> ResponseEntity.ok(sauService.toDetalhe(m, true)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** SAU responde: registra o atendente logado (auditoria) e notifica o paciente. */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ManifestacaoDetalheResponse responder(@PathVariable Long id,
            @Valid @RequestBody MensagemSauRequest request, @AuthenticationPrincipal Jwt jwt) {
        Manifestacao m = sauService.responderComoSau(obter(id), uidDoToken(jwt), jwt.getClaimAsString("nome"),
                request.texto());
        return sauService.toDetalhe(m, true);
    }

    /** SAU marca a manifestação como fechada. */
    @PostMapping("/{id}/fechar")
    @Transactional
    public ManifestacaoDetalheResponse fechar(@PathVariable Long id) {
        return sauService.toDetalhe(sauService.fechar(obter(id)), true);
    }

    private Manifestacao obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manifestação não encontrada"));
    }

    private Long uidDoToken(Jwt jwt) {
        return jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }
}
