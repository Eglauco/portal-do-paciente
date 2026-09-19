package com.example.pop.prontuario;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.motivofalta.MotivoFalta;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/prontuario")
public class ProntuarioController {

    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "numeroAtendimento", "numeroAtendimento",
            "paciente", "agendamento.paciente.nome",
            "especialidade", "agendamento.especialidade.nome",
            "unidade", "agendamento.unidadeSaude.nome",
            "dataHora", "agendamento.dataHora");
    /** Ordenação usada quando nada é escolhido na tela: por nome do paciente (A→Z). */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "agendamento.paciente.nome", "id");

    private final ProntuarioRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final StorageService storageService;
    private final PushService pushService;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final ExportacaoService exportacaoService;
    private final TipoDocumentoProntuarioRepository tipoRepository;
    private final ProntuarioIaService iaService;
    private final ProntuarioIaOrchestrator iaOrchestrator;

    public ProntuarioController(ProntuarioRepository repository, AgendamentoRepository agendamentoRepository,
            StorageService storageService, PushService pushService, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, ExportacaoService exportacaoService,
            TipoDocumentoProntuarioRepository tipoRepository, ProntuarioIaService iaService,
            ProntuarioIaOrchestrator iaOrchestrator) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.storageService = storageService;
        this.pushService = pushService;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.exportacaoService = exportacaoService;
        this.tipoRepository = tipoRepository;
        this.iaService = iaService;
        this.iaOrchestrator = iaOrchestrator;
    }

    @GetMapping
    public Pagina<ProntuarioAdminResponse> listar(
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String especialidade,
            @RequestParam(required = false) StatusAlertaProntuario status,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<Prontuario> resultado = repository.search(numero == null ? "" : numero, pacienteId, unidadeId,
                especialidade == null ? "" : especialidade.trim(), status, pageable);
        List<ProntuarioAdminResponse> content = resultado.getContent().stream().map(ProntuarioAdminResponse::from).toList();

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
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String especialidade,
            @RequestParam(required = false) StatusAlertaProntuario status,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        List<Prontuario> dados = repository.search(numero == null ? "" : numero, pacienteId, unidadeId,
                especialidade == null ? "" : especialidade.trim(), status,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<Prontuario>> cols = ExportacaoService.filtrar(colunasProntuario(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Prontuários", filtrosProntuario(numero, pacienteId, unidadeId, especialidade), cols, dados)
                : exportacaoService.excel("Prontuários", cols, dados);
        String nome = "prontuarios-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasProntuario().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosProntuario(String numero, Long pacienteId, Long unidadeId, String especialidade) {
        String paciente = pacienteId == null ? "Todos"
                : pacienteRepository.findById(pacienteId).map(p -> p.getNome()).orElse("#" + pacienteId);
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        return List.of(
                new FiltroAplicado("Nº atendimento", numero == null || numero.isBlank() ? "Todos" : numero),
                new FiltroAplicado("Paciente", paciente),
                new FiltroAplicado("Unidade", unidade),
                new FiltroAplicado("Especialidade", especialidade == null || especialidade.isBlank() ? "Todas" : especialidade));
    }

    private static List<ColunaExport<Prontuario>> colunasProntuario() {
        return List.of(
                ColunaExport.de("Nº atendimento", Prontuario::getNumeroAtendimento),
                ColunaExport.de("Paciente", p -> p.getAgendamento().getPaciente().getNome()),
                ColunaExport.de("CPF do paciente", p -> formatarCpf(p.getAgendamento().getPaciente().getCpf())),
                ColunaExport.de("Prontuário do paciente", p -> texto(p.getAgendamento().getPaciente().getProntuario())),
                ColunaExport.de("Telefone do paciente", p -> p.getAgendamento().getPaciente().getTelefonesAdicionais() == null ? ""
                        : p.getAgendamento().getPaciente().getTelefonesAdicionais().stream()
                                .map(ProntuarioController::formatarTelefone).collect(java.util.stream.Collectors.joining("; "))),
                ColunaExport.de("Especialidade", p -> p.getAgendamento().getEspecialidade().getNome()),
                ColunaExport.de("Profissional", p -> p.getAgendamento().getProfissionalSaude().getNome()),
                ColunaExport.de("Procedimento", p -> p.getAgendamento().getProcedimento().getNome()),
                ColunaExport.de("Unidade", p -> p.getAgendamento().getUnidadeSaude().getNome()),
                ColunaExport.de("Atendimento",
                        p -> p.getAgendamento().getDataHora() == null ? "" : p.getAgendamento().getDataHora().format(DATA_HORA)),
                ColunaExport.de("Status", p -> p.getAgendamento().getStatusAgendamento().getDescricao()),
                ColunaExport.de("Justificativa da falta", p -> texto(p.getAgendamento().getJustificativaFalta())),
                ColunaExport.de("Falta justificada em",
                        p -> p.getAgendamento().getFaltaJustificadaEm() == null ? ""
                                : p.getAgendamento().getFaltaJustificadaEm().format(DATA_HORA)),
                ColunaExport.de("Motivos da falta", p -> p.getAgendamento().getMotivosFalta() == null ? ""
                        : p.getAgendamento().getMotivosFalta().stream().map(MotivoFalta::getMotivo)
                                .collect(java.util.stream.Collectors.joining("; "))),
                ColunaExport.de("Status da análise", p -> p.getStatusAlerta() == null ? "" : p.getStatusAlerta().getDescricao()),
                ColunaExport.de("Documentos", p -> String.valueOf(p.getDocumentos().size())),
                ColunaExport.de("Nomes dos documentos", p -> p.getDocumentos() == null ? ""
                        : p.getDocumentos().stream().map(Documento::getNome)
                                .collect(java.util.stream.Collectors.joining("; "))));
    }

    private static String texto(String v) {
        return v == null ? "" : v;
    }

    /** Formata o CPF (só dígitos) como 000.000.000-00; devolve vazio se não tiver 11 dígitos. */
    private static String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf == null ? "" : cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    /** Formata o telefone (só dígitos) no padrão brasileiro; devolve o valor original se não reconhecer. */
    private static String formatarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return "";
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7);
        }
        if (digitos.length() == 10) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6);
        }
        return telefone;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProntuarioAdminDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> ResponseEntity.ok(ProntuarioAdminDetalheResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProntuarioAdminDetalheResponse criar(@Valid @RequestBody ProntuarioRequest request) {
        if (repository.existsByNumeroAtendimento(request.numeroAtendimento().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
        }
        Prontuario prontuario = new Prontuario();
        List<Documento> novos = aplicar(prontuario, request);
        Prontuario salvo = repository.save(prontuario);
        ProntuarioAdminDetalheResponse resposta = ProntuarioAdminDetalheResponse.from(salvo);
        // Notifica o paciente dono sobre o novo prontuário.
        pushService.notificarProntuario(salvo.getAgendamento().getPaciente().getId(), true);
        dispararAnalise(novos);
        return resposta;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProntuarioAdminDetalheResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody ProntuarioRequest request) {
        return repository.findById(id)
                .map(prontuario -> {
                    if (repository.existsByNumeroAtendimentoAndIdNot(request.numeroAtendimento().trim(), id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
                    }
                    List<Documento> novos = aplicar(prontuario, request);
                    Prontuario salvo = repository.save(prontuario);
                    ProntuarioAdminDetalheResponse resposta = ProntuarioAdminDetalheResponse.from(salvo);
                    // Notifica o paciente dono se novos documentos foram adicionados.
                    if (!novos.isEmpty()) {
                        pushService.notificarProntuario(salvo.getAgendamento().getPaciente().getId(), false);
                    }
                    dispararAnalise(novos);
                    return ResponseEntity.ok(resposta);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Valida um documento marcado pela IA como "Aguardando validação": registra quem validou e
     * quando, e recalcula o status do prontuário.
     */
    @PostMapping("/documento/{documentoId}/validar")
    public ResponseEntity<ProntuarioAdminDetalheResponse> validar(@PathVariable Long documentoId,
            @AuthenticationPrincipal Jwt jwt) {
        Prontuario p = iaService.validar(documentoId, uidDoToken(jwt));
        return ResponseEntity.ok(ProntuarioAdminDetalheResponse.from(p));
    }

    /** Reprocessa a análise de um documento por IA (após ajustar o prompt do tipo, ou se falhou). */
    @PostMapping("/documento/{documentoId}/reanalisar")
    public ResponseEntity<Void> reanalisar(@PathVariable Long documentoId) {
        iaOrchestrator.analisar(documentoId, true); // gate revalidado no orquestrador
        return ResponseEntity.accepted().build();
    }

    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number n ? n.longValue() : null;
    }

    /** Dispara a análise por IA (assíncrona) dos documentos novos que têm tipo definido. */
    private void dispararAnalise(List<Documento> novos) {
        for (Documento d : novos) {
            if (d.getId() != null && d.getTipo() != null) {
                iaOrchestrator.analisar(d.getId(), false);
            }
        }
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

    /**
     * Aplica o request ao prontuário fazendo MERGE dos documentos por URL: mantém os documentos que
     * continuam (preservando a análise por IA já feita), remove os que saíram (orphanRemoval) e cria
     * os novos. Devolve a lista dos documentos NOVOS (para disparar a análise por IA só neles).
     */
    private List<Documento> aplicar(Prontuario prontuario, ProntuarioRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agendamento não encontrado"));
        prontuario.setAgendamento(agendamento);
        prontuario.setNumeroAtendimento(request.numeroAtendimento().trim());

        List<Documento> atuais = prontuario.getDocumentos();
        java.util.Set<String> urlsRequest = new java.util.HashSet<>();
        for (DocumentoRequest dr : request.documentos()) {
            if (dr.url() != null) {
                urlsRequest.add(dr.url());
            }
        }
        // Remove os documentos que saíram (orphanRemoval apaga a linha e o resto).
        atuais.removeIf(d -> d.getUrl() == null || !urlsRequest.contains(d.getUrl()));
        Map<String, Documento> porUrl = new HashMap<>();
        for (Documento d : atuais) {
            if (d.getUrl() != null) {
                porUrl.put(d.getUrl(), d);
            }
        }

        List<Documento> novos = new ArrayList<>();
        for (DocumentoRequest dr : request.documentos()) {
            TipoDocumentoProntuario tipo = dr.tipoId() == null ? null
                    : tipoRepository.findById(dr.tipoId()).orElse(null);
            Documento existente = dr.url() == null ? null : porUrl.get(dr.url());
            if (existente != null) {
                existente.setNome(dr.nome().trim());
                existente.setTipo(tipo); // mantém a análise já feita
            } else {
                Documento d = new Documento();
                d.setNome(dr.nome().trim());
                d.setUrl(dr.url());
                d.setTipo(tipo);
                d.setProntuario(prontuario);
                atuais.add(d);
                novos.add(d);
            }
        }
        return novos;
    }
}
