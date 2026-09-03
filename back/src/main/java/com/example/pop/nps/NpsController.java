package com.example.pop.nps;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/nps")
public class NpsController {

    private static final int TAMANHO_MAXIMO = 100;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NpsRepository repository;
    private final NpsService npsService;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final ExportacaoService exportacaoService;

    public NpsController(NpsRepository repository, NpsService npsService, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.npsService = npsService;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<NpsResponse> listar(
            @RequestParam(required = false) StatusNps status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<Nps> resultado = repository.search(status, pacienteId, unidadeId, pageable);
        List<NpsResponse> content = resultado.getContent().stream().map(NpsResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /**
     * Exporta as avaliações que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) StatusNps status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId) {
        List<Nps> dados = repository.search(status, pacienteId, unidadeId, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Nps::getCriadoEm).reversed())
                .toList();
        List<ColunaExport<Nps>> colunas = colunasNps();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("NPS", filtrosNps(status, pacienteId, unidadeId), colunas, dados)
                : exportacaoService.excel("NPS", colunas, dados);
        String nome = "nps-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosNps(StatusNps status, Long pacienteId, Long unidadeId) {
        String paciente = pacienteId == null ? "Todos"
                : pacienteRepository.findById(pacienteId).map(Paciente::getNome).orElse("#" + pacienteId);
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(Unidade::getNome).orElse("#" + unidadeId);
        return List.of(
                new FiltroAplicado("Status", status != null ? status.getDescricao() : "Todos"),
                new FiltroAplicado("Paciente", paciente),
                new FiltroAplicado("Unidade", unidade));
    }

    private static List<ColunaExport<Nps>> colunasNps() {
        return List.of(
                ColunaExport.de("Atendimento",
                        n -> n.getAgendamento().getDataHora() == null ? "" : n.getAgendamento().getDataHora().format(DATA_HORA)),
                ColunaExport.de("Paciente", n -> n.getAgendamento().getPaciente().getNome()),
                ColunaExport.de("Unidade", n -> n.getAgendamento().getUnidadeSaude().getNome()),
                ColunaExport.de("Especialidade", n -> n.getAgendamento().getEspecialidade().getNome()),
                ColunaExport.de("Profissional", n -> n.getAgendamento().getProfissionalSaude().getNome()),
                ColunaExport.de("Média", n -> n.getMedia() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", n.getMedia())),
                ColunaExport.de("Status", n -> n.getStatus().getDescricao()),
                ColunaExport.de("Gerado em", n -> n.getCriadoEm() == null ? "" : n.getCriadoEm().format(DATA_HORA)),
                ColunaExport.de("Respondido em", n -> n.getRespondidoEm() == null ? "" : n.getRespondidoEm().format(DATA_HORA)),
                ColunaExport.de("Observação", Nps::getObservacao));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<NpsDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(nps -> ResponseEntity.ok(NpsDetalheResponse.from(nps)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Registra a resposta do paciente (uma nota em estrelas 1 a 5 por categoria + observação opcional). */
    @PostMapping("/{id}/responder")
    @Transactional
    public ResponseEntity<NpsDetalheResponse> responder(@PathVariable Long id,
            @Valid @RequestBody ResponderNpsRequest request) {
        Nps nps = obter(id);
        return ResponseEntity.ok(NpsDetalheResponse.from(npsService.responder(nps, request)));
    }

    @PostMapping("/{id}/expirar")
    @Transactional
    public ResponseEntity<NpsDetalheResponse> expirar(@PathVariable Long id) {
        Nps nps = obter(id);
        nps.setStatus(StatusNps.EXPIRADO);
        return ResponseEntity.ok(NpsDetalheResponse.from(repository.save(nps)));
    }

    private Nps obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPS não encontrado"));
    }
}
