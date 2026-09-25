package com.example.pop.agendamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.motivofalta.MotivoFalta;
import com.example.pop.motivofalta.MotivoFaltaRepository;
import com.example.pop.nps.NpsService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.prontuario.TermoAssinaturaService;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.push.PushService;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/agendamento")
public class AgendamentoController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "dataHora", "dataHora",
            "paciente", "paciente.nome",
            "especialidade", "especialidade.nome",
            "profissional", "profissionalSaude.nome",
            "procedimento", "procedimento.nome",
            "status", "statusAgendamento",
            "entregaResumo", "entregaResumo");
    /** Ordenação usada quando nada é escolhido na tela (mais recentes primeiro, desempate por id). */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.DESC, "dataHora")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private final AgendamentoRepository repository;
    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final ProcedimentoRepository procedimentoRepository;
    private final MotivoFaltaRepository motivoFaltaRepository;
    private final NpsService npsService;
    private final PushService pushService;
    private final ExportacaoService exportacaoService;
    private final AgendamentoLogService logService;
    private final AgendamentoEntregaService entregaService;
    private final AgendamentoEntregaRepository entregaRepository;
    private final TermoAssinaturaService termoAssinaturaService;

    public AgendamentoController(AgendamentoRepository repository, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, EspecialidadeRepository especialidadeRepository,
            ProfissionalSaudeRepository profissionalRepository, ProcedimentoRepository procedimentoRepository,
            MotivoFaltaRepository motivoFaltaRepository, NpsService npsService, PushService pushService,
            ExportacaoService exportacaoService, AgendamentoLogService logService,
            AgendamentoEntregaService entregaService, AgendamentoEntregaRepository entregaRepository,
            TermoAssinaturaService termoAssinaturaService) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.motivoFaltaRepository = motivoFaltaRepository;
        this.npsService = npsService;
        this.pushService = pushService;
        this.exportacaoService = exportacaoService;
        this.logService = logService;
        this.entregaService = entregaService;
        this.entregaRepository = entregaRepository;
        this.termoAssinaturaService = termoAssinaturaService;
    }

    /**
     * Lista agendamentos de forma paginada (mais recentes primeiro), com filtros
     * opcionais por status e paciente. Tamanho de página limitado a {@value #TAMANHO_MAXIMO}.
     */
    @GetMapping
    public Pagina<AgendamentoResponse> listar(
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String especialidadeNome,
            @RequestParam(required = false) String profissionalNome,
            @RequestParam(required = false) EstadoEntrega entregaResumo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<Agendamento> resultado = repository.search(status, padraoNome(nome), padraoNome(especialidadeNome),
                padraoNome(profissionalNome), entregaResumo, inicioDoDia(data), fimDoDia(data), unidadeId, pageable);
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

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta os agendamentos que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String especialidadeNome,
            @RequestParam(required = false) String profissionalNome,
            @RequestParam(required = false) EstadoEntrega entregaResumo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        List<Agendamento> dados = repository.search(status, padraoNome(nome), padraoNome(especialidadeNome),
                padraoNome(profissionalNome), entregaResumo, inicioDoDia(data), fimDoDia(data), unidadeId,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO)))
                .getContent();
        List<ColunaExport<Agendamento>> cols = ExportacaoService.filtrar(colunasAgendamento(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Agendamentos", filtrosAgendamento(status, nome, especialidadeNome,
                        profissionalNome, entregaResumo, data, unidadeId), cols, dados)
                : exportacaoService.excel("Agendamentos", cols, dados);
        String nomeArquivo = "agendamentos-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasAgendamento().stream().map(ColunaExport::titulo).toList();
    }

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosAgendamento(StatusAgendamento status, String nome, String especialidadeNome,
            String profissionalNome, EstadoEntrega entregaResumo, LocalDate data, Long unidadeId) {
        List<FiltroAplicado> filtros = new ArrayList<>();
        filtros.add(new FiltroAplicado("Status", status != null ? status.getDescricao() : "Todos"));
        if (nome != null && !nome.isBlank()) {
            filtros.add(new FiltroAplicado("Paciente", nome.trim()));
        }
        if (especialidadeNome != null && !especialidadeNome.isBlank()) {
            filtros.add(new FiltroAplicado("Especialidade", especialidadeNome.trim()));
        }
        if (profissionalNome != null && !profissionalNome.isBlank()) {
            filtros.add(new FiltroAplicado("Profissional", profissionalNome.trim()));
        }
        if (entregaResumo != null) {
            filtros.add(new FiltroAplicado("Notificação", entregaResumo.getDescricao()));
        }
        if (data != null) {
            filtros.add(new FiltroAplicado("Data", data.format(DATA)));
        }
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        filtros.add(new FiltroAplicado("Unidade", unidade));
        return filtros;
    }

    /**
     * Padrão LIKE (minúsculo, com curingas) para a busca parcial por nome; nulo se vazio.
     * Escapa os metacaracteres LIKE ('\', '%', '_') do texto digitado para que funcionem como
     * literais — o '\' é declarado como escape na cláusula {@code like ... escape '\'} da query.
     */
    private static String padraoNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String escapado = nome.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\") // a barra primeiro, senão re-escaparia os escapes inseridos depois
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapado + "%";
    }

    private static LocalDateTime inicioDoDia(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    /**
     * Fim do dia com precisão de MICROssegundos (não {@link LocalTime#MAX}): a coluna
     * data_hora é timestamp(6), e 23:59:59.999999999 (nanos) seria ARREDONDADO pelo driver
     * para 00:00:00 do dia seguinte, incluindo indevidamente a meia-noite seguinte no período.
     */
    private static LocalDateTime fimDoDia(LocalDate d) {
        return d == null ? null : d.atTime(LocalTime.of(23, 59, 59, 999_999_000));
    }

    /** Todas as colunas disponíveis do agendamento (o usuário escolhe quais exportar). */
    private static List<ColunaExport<Agendamento>> colunasAgendamento() {
        return List.of(
                ColunaExport.de("Código", a -> String.valueOf(a.getId())),
                ColunaExport.de("Data/Hora", a -> a.getDataHora() == null ? "" : a.getDataHora().format(DATA_HORA)),
                ColunaExport.de("Paciente", a -> a.getPaciente().getNome()),
                ColunaExport.de("CPF do paciente", a -> formatarCpf(a.getPaciente().getCpf())),
                ColunaExport.de("Telefone do paciente", a -> a.getPaciente().getTelefonesAdicionais() == null ? ""
                        : a.getPaciente().getTelefonesAdicionais().stream().map(AgendamentoController::formatarTelefone)
                                .collect(java.util.stream.Collectors.joining("; "))),
                ColunaExport.de("Prontuário", a -> texto(a.getPaciente().getProntuario())),
                ColunaExport.de("Unidade", a -> a.getUnidadeSaude().getNome()),
                ColunaExport.de("Especialidade", a -> a.getEspecialidade().getNome()),
                ColunaExport.de("Profissional", a -> a.getProfissionalSaude().getNome()),
                ColunaExport.de("Procedimento", a -> a.getProcedimento().getNome()),
                ColunaExport.de("Status", a -> a.getStatusAgendamento().getDescricao()),
                ColunaExport.de("Falta justificada", a -> a.getFaltaJustificadaEm() != null ? "Sim" : "Não"),
                ColunaExport.de("Justificada em",
                        a -> a.getFaltaJustificadaEm() == null ? "" : a.getFaltaJustificadaEm().format(DATA_HORA)),
                ColunaExport.de("Justificativa", a -> texto(a.getJustificativaFalta())),
                ColunaExport.de("Motivos da falta",
                        a -> a.getMotivosFalta().stream().map(MotivoFalta::getMotivo).reduce((x, y) -> x + "; " + y).orElse("")));
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
    public ResponseEntity<AgendamentoResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(a -> ResponseEntity.ok(AgendamentoResponse.from(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Destinatários da notificação deste agendamento e o estado de entrega de cada um. */
    @GetMapping("/{id}/entrega")
    public ResponseEntity<List<AgendamentoEntregaResponse>> entrega(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build(); // distingue "não existe" de "sem dados de entrega"
        }
        List<AgendamentoEntregaResponse> lista = entregaRepository.findByAgendamento_IdOrderByIdAsc(id).stream()
                .map(AgendamentoEntregaResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResponse criar(@Valid @RequestBody AgendamentoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Agendamento agendamento = new Agendamento();
        aplicar(agendamento, request);
        // Regra de negócio: todo novo agendamento nasce aguardando confirmação do paciente.
        agendamento.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Agendamento salvo = repository.save(agendamento);
        // Primeira linha do histórico: a unidade criou o agendamento (status inicial).
        logService.registrarDaUnidade(salvo, null, StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, uidDoToken(jwt));
        // Notifica o paciente/responsáveis (push) e RASTREIA a entrega por destinatário.
        entregaService.notificarNovoAgendamento(salvo);
        return AgendamentoResponse.from(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody AgendamentoRequest request, @AuthenticationPrincipal Jwt jwt) {
        return repository.findById(id)
                .map(agendamento -> {
                    StatusAgendamento anterior = agendamento.getStatusAgendamento();
                    aplicar(agendamento, request);
                    if (request.statusAgendamento() != null) {
                        agendamento.setStatusAgendamento(request.statusAgendamento());
                    }
                    Agendamento salvo = repository.save(agendamento);
                    // Registra a troca de status feita pela unidade (só grava se de fato mudou).
                    logService.registrarDaUnidade(salvo, anterior, salvo.getStatusAgendamento(), uidDoToken(jwt));
                    // Regra: ao registrar a presença do paciente, gera o NPS vinculado ao atendimento.
                    npsService.gerarSeNecessario(salvo);
                    // Regra: na presença, se o procedimento tiver termos (TCLE), gera as pendências de
                    // assinatura no prontuário (best-effort — não bloqueia o registro da presença).
                    try {
                        termoAssinaturaService.dispararSeNecessario(salvo);
                    } catch (RuntimeException ignored) {
                        // não impede a atualização do agendamento
                    }
                    // Ao MARCAR falta (transição), notifica o paciente para justificar a ausência.
                    if (salvo.getStatusAgendamento() == StatusAgendamento.FALTA_PACIENTE
                            && anterior != StatusAgendamento.FALTA_PACIENTE) {
                        pushService.notificarFaltaPaciente(salvo);
                    }
                    return ResponseEntity.ok(AgendamentoResponse.from(salvo));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Confirmação do paciente (app): move o status para PACIENTE_CONFIRMOU. */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<AgendamentoResponse> confirmar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return alterarStatus(id, StatusAgendamento.PACIENTE_CONFIRMOU, uidDoToken(jwt));
    }

    /** Cancelamento pelo paciente (app): move o status para CANCELADO_PELO_PACIENTE. */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return alterarStatus(id, StatusAgendamento.CANCELADO_PELO_PACIENTE, uidDoToken(jwt));
    }

    /**
     * Linha do tempo das trocas de status do agendamento (auditoria): quem fez cada
     * mudança (paciente, responsável ou unidade). Espelha o histórico do chat.
     */
    @GetMapping("/{id}/logs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AgendamentoLogResponse>> logs(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logService.listar(id));
    }

    /**
     * Justificativa da falta pelo paciente (app): registra os motivos selecionados e o
     * texto livre. Só permitido quando o agendamento está em FALTA_PACIENTE.
     */
    @PostMapping("/{id}/justificar-falta")
    @Transactional
    public ResponseEntity<AgendamentoResponse> justificarFalta(@PathVariable Long id,
            @Valid @RequestBody JustificarFaltaRequest request) {
        return repository.findById(id)
                .map(agendamento -> {
                    if (agendamento.getStatusAgendamento() != StatusAgendamento.FALTA_PACIENTE) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "O agendamento não está marcado como falta do paciente");
                    }
                    List<MotivoFalta> motivos = motivoFaltaRepository.findAllById(request.motivoIds());
                    agendamento.setMotivosFalta(motivos);
                    String texto = request.justificativa() == null ? null : request.justificativa().trim();
                    agendamento.setJustificativaFalta(texto == null || texto.isBlank() ? null : texto);
                    agendamento.setFaltaJustificadaEm(LocalDateTime.now());
                    return ResponseEntity.ok(AgendamentoResponse.from(repository.save(agendamento)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<AgendamentoResponse> alterarStatus(Long id, StatusAgendamento status, Long usuarioId) {
        return repository.findById(id)
                .map(agendamento -> {
                    StatusAgendamento antes = agendamento.getStatusAgendamento();
                    agendamento.setStatusAgendamento(status);
                    Agendamento salvo = repository.save(agendamento);
                    // Feita pela unidade (back-office); grava só se o status mudou de fato.
                    logService.registrarDaUnidade(salvo, antes, status, usuarioId);
                    return ResponseEntity.ok(AgendamentoResponse.from(salvo));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** uid do atendente logado (nulo se não houver token — ex.: chamadas diretas em teste). */
    private Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
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
