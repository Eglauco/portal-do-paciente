package com.example.pop.agendamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.motivofalta.MotivoFalta;
import com.example.pop.motivofalta.MotivoFaltaRepository;
import com.example.pop.nps.NpsService;
import com.example.pop.paciente.PacienteRepository;
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

    public AgendamentoController(AgendamentoRepository repository, PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository, EspecialidadeRepository especialidadeRepository,
            ProfissionalSaudeRepository profissionalRepository, ProcedimentoRepository procedimentoRepository,
            MotivoFaltaRepository motivoFaltaRepository, NpsService npsService, PushService pushService,
            ExportacaoService exportacaoService) {
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
    }

    /**
     * Lista agendamentos de forma paginada (mais recentes primeiro), com filtros
     * opcionais por status e paciente. Tamanho de página limitado a {@value #TAMANHO_MAXIMO}.
     */
    @GetMapping
    public Pagina<AgendamentoResponse> listar(
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataHora"));
        Page<Agendamento> resultado = repository.search(status, pacienteId, unidadeId, pageable);
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
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) List<String> colunas) {
        List<Agendamento> dados = repository.search(status, pacienteId, unidadeId, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Agendamento::getDataHora).reversed())
                .toList();
        List<ColunaExport<Agendamento>> cols = ExportacaoService.filtrar(colunasAgendamento(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Agendamentos", filtrosAgendamento(status, unidadeId), cols, dados)
                : exportacaoService.excel("Agendamentos", cols, dados);
        String nome = "agendamentos-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasAgendamento().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosAgendamento(StatusAgendamento status, Long unidadeId) {
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(u -> u.getNome()).orElse("#" + unidadeId);
        return List.of(
                new FiltroAplicado("Status", status != null ? status.getDescricao() : "Todos"),
                new FiltroAplicado("Unidade", unidade));
    }

    /** Todas as colunas disponíveis do agendamento (o usuário escolhe quais exportar). */
    private static List<ColunaExport<Agendamento>> colunasAgendamento() {
        return List.of(
                ColunaExport.de("Código", a -> String.valueOf(a.getId())),
                ColunaExport.de("Data/Hora", a -> a.getDataHora() == null ? "" : a.getDataHora().format(DATA_HORA)),
                ColunaExport.de("Paciente", a -> a.getPaciente().getNome()),
                ColunaExport.de("CPF do paciente", a -> formatarCpf(a.getPaciente().getCpf())),
                ColunaExport.de("Telefone do paciente", a -> formatarTelefone(a.getPaciente().getTelefone())),
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoResponse criar(@Valid @RequestBody AgendamentoRequest request) {
        Agendamento agendamento = new Agendamento();
        aplicar(agendamento, request);
        // Regra de negócio: todo novo agendamento nasce aguardando confirmação do paciente.
        agendamento.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Agendamento salvo = repository.save(agendamento);
        // Notifica o paciente (push) para confirmar/cancelar o novo agendamento.
        pushService.notificarNovoAgendamento(salvo);
        return AgendamentoResponse.from(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody AgendamentoRequest request) {
        return repository.findById(id)
                .map(agendamento -> {
                    StatusAgendamento anterior = agendamento.getStatusAgendamento();
                    aplicar(agendamento, request);
                    if (request.statusAgendamento() != null) {
                        agendamento.setStatusAgendamento(request.statusAgendamento());
                    }
                    Agendamento salvo = repository.save(agendamento);
                    // Regra: ao registrar a presença do paciente, gera o NPS vinculado ao atendimento.
                    npsService.gerarSeNecessario(salvo);
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
    public ResponseEntity<AgendamentoResponse> confirmar(@PathVariable Long id) {
        return alterarStatus(id, StatusAgendamento.PACIENTE_CONFIRMOU);
    }

    /** Cancelamento pelo paciente (app): move o status para CANCELADO_PELO_PACIENTE. */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<AgendamentoResponse> cancelar(@PathVariable Long id) {
        return alterarStatus(id, StatusAgendamento.CANCELADO_PELO_PACIENTE);
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
