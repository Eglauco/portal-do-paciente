package com.example.pop.prontuario;

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

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/prontuario")
public class ProntuarioController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ProntuarioRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final StorageService storageService;
    private final PushService pushService;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final ExportacaoService exportacaoService;

    public ProntuarioController(ProntuarioRepository repository, AgendamentoRepository agendamentoRepository,
            StorageService storageService, PushService pushService, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.storageService = storageService;
        this.pushService = pushService;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<ProntuarioResponse> listar(
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id"));
        Page<Prontuario> resultado = repository.search(numero == null ? "" : numero, pacienteId, unidadeId, pageable);
        List<ProntuarioResponse> content = resultado.getContent().stream().map(ProntuarioResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta os prontuários que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId) {
        List<Prontuario> dados = repository.search(numero == null ? "" : numero, pacienteId, unidadeId, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Prontuario::getId).reversed())
                .toList();
        List<ColunaExport<Prontuario>> colunas = colunasProntuario();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Prontuários", filtrosProntuario(numero, pacienteId, unidadeId), colunas, dados)
                : exportacaoService.excel("Prontuários", colunas, dados);
        String nome = "prontuarios-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosProntuario(String numero, Long pacienteId, Long unidadeId) {
        String paciente = pacienteId == null ? "Todos"
                : pacienteRepository.findById(pacienteId).map(p -> p.getNome()).orElse("#" + pacienteId);
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        return List.of(
                new FiltroAplicado("Nº atendimento", numero == null || numero.isBlank() ? "Todos" : numero),
                new FiltroAplicado("Paciente", paciente),
                new FiltroAplicado("Unidade", unidade));
    }

    private static List<ColunaExport<Prontuario>> colunasProntuario() {
        return List.of(
                ColunaExport.de("Nº atendimento", Prontuario::getNumeroAtendimento),
                ColunaExport.de("Paciente", p -> p.getAgendamento().getPaciente().getNome()),
                ColunaExport.de("Especialidade", p -> p.getAgendamento().getEspecialidade().getNome()),
                ColunaExport.de("Profissional", p -> p.getAgendamento().getProfissionalSaude().getNome()),
                ColunaExport.de("Procedimento", p -> p.getAgendamento().getProcedimento().getNome()),
                ColunaExport.de("Unidade", p -> p.getAgendamento().getUnidadeSaude().getNome()),
                ColunaExport.de("Atendimento",
                        p -> p.getAgendamento().getDataHora() == null ? "" : p.getAgendamento().getDataHora().format(DATA_HORA)),
                ColunaExport.de("Status", p -> p.getAgendamento().getStatusAgendamento().getDescricao()),
                ColunaExport.de("Documentos", p -> String.valueOf(p.getDocumentos().size())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProntuarioDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> ResponseEntity.ok(ProntuarioDetalheResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProntuarioDetalheResponse criar(@Valid @RequestBody ProntuarioRequest request) {
        if (repository.existsByNumeroAtendimento(request.numeroAtendimento().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
        }
        Prontuario prontuario = new Prontuario();
        aplicar(prontuario, request);
        Prontuario salvo = repository.save(prontuario);
        ProntuarioDetalheResponse resposta = ProntuarioDetalheResponse.from(salvo);
        // Notifica o paciente dono sobre o novo prontuário.
        pushService.notificarProntuario(salvo.getAgendamento().getPaciente().getId(), true);
        return resposta;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProntuarioDetalheResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody ProntuarioRequest request) {
        return repository.findById(id)
                .map(prontuario -> {
                    if (repository.existsByNumeroAtendimentoAndIdNot(request.numeroAtendimento().trim(), id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
                    }
                    int documentosAntes = prontuario.getDocumentos().size();
                    aplicar(prontuario, request);
                    Prontuario salvo = repository.save(prontuario);
                    ProntuarioDetalheResponse resposta = ProntuarioDetalheResponse.from(salvo);
                    // Notifica o paciente dono se novos documentos foram adicionados.
                    if (request.documentos().size() > documentosAntes) {
                        pushService.notificarProntuario(salvo.getAgendamento().getPaciente().getId(), false);
                    }
                    return ResponseEntity.ok(resposta);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        Prontuario prontuario = repository.findById(id).orElse(null);
        if (prontuario == null) {
            return ResponseEntity.notFound().build();
        }
        // Remove os arquivos do S3 para não deixar lixo.
        prontuario.getDocumentos().forEach(d -> {
            try {
                storageService.excluirPorUrl(d.getUrl());
            } catch (RuntimeException ignored) {
                // não impede a exclusão do prontuário se a limpeza falhar
            }
        });
        repository.delete(prontuario);
        return ResponseEntity.noContent().build();
    }

    private void aplicar(Prontuario prontuario, ProntuarioRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agendamento não encontrado"));
        prontuario.setAgendamento(agendamento);
        prontuario.setNumeroAtendimento(request.numeroAtendimento().trim());

        List<Documento> documentos = request.documentos().stream().map(dr -> {
            Documento d = new Documento();
            d.setNome(dr.nome().trim());
            d.setUrl(dr.url());
            return d;
        }).toList();
        prontuario.substituirDocumentos(documentos);
    }
}
