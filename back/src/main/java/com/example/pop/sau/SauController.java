package com.example.pop.sau;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

/** SAU do lado do admin (back-office): lista, vê, responde e fecha manifestações. */
@RestController
@RequestMapping("/sau")
public class SauController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ManifestacaoRepository repository;
    private final SauService sauService;
    private final UnidadeRepository unidadeRepository;
    private final TipoManifestacaoRepository tipoRepository;
    private final ExportacaoService exportacaoService;

    public SauController(ManifestacaoRepository repository, SauService sauService,
            UnidadeRepository unidadeRepository, TipoManifestacaoRepository tipoRepository,
            ExportacaoService exportacaoService) {
        this.repository = repository;
        this.sauService = sauService;
        this.unidadeRepository = unidadeRepository;
        this.tipoRepository = tipoRepository;
        this.exportacaoService = exportacaoService;
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

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta as manifestações que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long tipoId,
            @RequestParam(required = false) StatusManifestacao status) {
        List<ManifestacaoResponse> dados = repository.search(unidadeId, tipoId, status, Pageable.unpaged())
                .getContent().stream()
                .map(sauService::toResponse)
                .sorted(Comparator.comparing(ManifestacaoResponse::atualizadoEm).reversed())
                .toList();
        List<ColunaExport<ManifestacaoResponse>> colunas = colunasSau();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Manifestações (SAU)", filtrosSau(unidadeId, tipoId, status), colunas, dados)
                : exportacaoService.excel("Manifestações (SAU)", colunas, dados);
        String nome = "sau-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosSau(Long unidadeId, Long tipoId, StatusManifestacao status) {
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        String tipo = tipoId == null ? "Todos"
                : tipoRepository.findById(tipoId).map(TipoManifestacao::getNome).orElse("#" + tipoId);
        return List.of(
                new FiltroAplicado("Unidade", unidade),
                new FiltroAplicado("Tipo", tipo),
                new FiltroAplicado("Status", status != null ? status.getDescricao() : "Todos"));
    }

    private static List<ColunaExport<ManifestacaoResponse>> colunasSau() {
        return List.of(
                ColunaExport.de("Aberta em", m -> m.criadoEm() == null ? "" : m.criadoEm().format(DATA_HORA)),
                ColunaExport.de("Atualizada em", m -> m.atualizadoEm() == null ? "" : m.atualizadoEm().format(DATA_HORA)),
                ColunaExport.de("Paciente", m -> m.paciente().nome()),
                ColunaExport.de("Unidade", m -> m.unidadeSaude().nome()),
                ColunaExport.de("Tipo", m -> m.tipo().nome()),
                ColunaExport.de("Status", ManifestacaoResponse::statusDescricao),
                ColunaExport.de("Avaliação", m -> m.avaliacaoNota() == null ? "" : m.avaliacaoNota() + "/5"),
                ColunaExport.de("Última resposta de", m -> m.ultimaMensagemDe() == null ? ""
                        : (m.ultimaMensagemDe() == AutorManifestacao.SAU ? "SAU" : "Paciente")),
                ColunaExport.de("Última mensagem", ManifestacaoResponse::ultimaMensagem));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ManifestacaoDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(m -> ResponseEntity.ok(sauService.toDetalhe(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** SAU responde: registra o atendente logado (auditoria) e notifica o paciente. */
    @PostMapping("/{id}/mensagem")
    @Transactional
    public ManifestacaoDetalheResponse responder(@PathVariable Long id,
            @Valid @RequestBody MensagemSauRequest request, @AuthenticationPrincipal Jwt jwt) {
        Manifestacao m = sauService.responderComoSau(obter(id), uidDoToken(jwt), request.texto());
        return sauService.toDetalhe(m);
    }

    /** SAU marca a manifestação como fechada. */
    @PostMapping("/{id}/fechar")
    @Transactional
    public ManifestacaoDetalheResponse fechar(@PathVariable Long id) {
        return sauService.toDetalhe(sauService.fechar(obter(id)));
    }

    private Manifestacao obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manifestação não encontrada"));
    }

    private Long uidDoToken(Jwt jwt) {
        return jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }
}
