package com.example.pop.siresp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agenda;
import com.example.pop.agendamento.AgendaRepository;
import com.example.pop.agendamento.Horario;
import com.example.pop.agendamento.HorarioLogService;
import com.example.pop.agendamento.HorarioRepository;
import com.example.pop.agendamento.HorarioEntregaService;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.especialidade.Especialidade;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Regra de criação do AGENDAMENTO a partir de um registro do SIRESP. Dois passos:
 * <ol>
 *   <li>{@link #avaliar(Siresp)} — resolve, pelo CÓDIGO DE INTEGRAÇÃO, TODAS as informações necessárias
 *       (especialidade, unidade executante, profissional, paciente, data/hora e o procedimento padrão) e diz o
 *       que falta. Nada é criado se faltar algo (não gera "registro pela metade").</li>
 *   <li>{@link #processarRegistro(Long, Long)} — recalcula o diagnóstico e, se estiver tudo completo e ainda não
 *       houver agendamento para este registro, cria o agendamento (deduplicando por {@code ID_AGE_CONSULTA_HOR}),
 *       grava o log de auditoria e notifica o paciente. É o que o botão "Reprocessar" e a importação chamam: o
 *       usuário vai corrigindo os códigos e reprocessando até tudo ficar verde.</li>
 * </ol>
 * Não é {@code @Transactional} de propósito (espelha o cadastro manual): cada save é uma tx curta e a notificação
 * (push) roda fora de transação.
 */
@Service
public class SirespAgendamentoService {

    private static final DateTimeFormatter DATA_HORA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter[] DATAS = {
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy") };
    private static final DateTimeFormatter[] HORAS = {
            DateTimeFormatter.ofPattern("HH:mm:ss"), DateTimeFormatter.ofPattern("HH:mm") };

    private final SirespRepository repository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final PacienteRepository pacienteRepository;
    private final ProcedimentoRepository procedimentoRepository;
    private final HorarioRepository agendamentoRepository;
    private final AgendaRepository agendaRepository;
    private final HorarioLogService agendamentoLogService;
    private final HorarioEntregaService entregaService;
    private final SirespConfigService configService;

    public SirespAgendamentoService(SirespRepository repository, EspecialidadeRepository especialidadeRepository,
            UnidadeRepository unidadeRepository, ProfissionalSaudeRepository profissionalRepository,
            PacienteRepository pacienteRepository, ProcedimentoRepository procedimentoRepository,
            HorarioRepository agendamentoRepository, AgendaRepository agendaRepository,
            HorarioLogService agendamentoLogService,
            HorarioEntregaService entregaService, SirespConfigService configService) {
        this.repository = repository;
        this.especialidadeRepository = especialidadeRepository;
        this.unidadeRepository = unidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.pacienteRepository = pacienteRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.agendaRepository = agendaRepository;
        this.agendamentoLogService = agendamentoLogService;
        this.entregaService = entregaService;
        this.configService = configService;
    }

    /** As peças necessárias ao agendamento + as linhas do diagnóstico. {@code completo()} = tudo resolvido. */
    public record Avaliacao(Especialidade especialidade, Unidade unidade, ProfissionalSaude profissional,
            Paciente paciente, LocalDateTime dataHora, Procedimento procedimento, List<String> linhas) {
        public boolean completo() {
            return especialidade != null && unidade != null && profissional != null
                    && paciente != null && dataHora != null && procedimento != null;
        }
    }

    /** Resolve todas as informações do agendamento a partir do registro e monta as linhas do diagnóstico. */
    public Avaliacao avaliar(Siresp s) {
        List<String> linhas = new ArrayList<>();

        String codEsp = trim(s.getIdEspecialidade());
        Especialidade esp = codEsp.isEmpty() ? null
                : especialidadeRepository.findByCodigoIntegracao(codEsp).orElse(null);
        linhas.add(codEsp.isEmpty() ? "Especialidade: código (ID_ESPECIALIDADE) não informado no XML."
                : esp != null ? "Especialidade: OK — " + esp.getNome() + "."
                        : "Especialidade: não encontrada pelo código de integração (" + codEsp + ").");

        String codUni = trim(s.getCodUnidadeExecutante());
        Unidade uni = codUni.isEmpty() ? null : unidadeRepository.findByCodigoIntegracao(codUni).orElse(null);
        linhas.add(codUni.isEmpty() ? "Unidade de saúde: código (COD_UNIDADE_EXECUTANTE) não informado no XML."
                : uni != null ? "Unidade de saúde: OK — " + uni.getNome() + "."
                        : "Unidade de saúde: não encontrada pelo código de integração (" + codUni + ").");

        String codProf = trim(s.getIdProfissional());
        ProfissionalSaude prof = codProf.isEmpty() ? null
                : profissionalRepository.findByCodigoIntegracao(codProf).orElse(null);
        linhas.add(codProf.isEmpty() ? "Profissional: código (ID_PROFISSIONAL) não informado no XML."
                : prof != null ? "Profissional: OK — " + prof.getNome() + "."
                        : "Profissional: não encontrado pelo código de integração (" + codProf + ").");

        String codPac = trim(s.getCodPaciente());
        String cpf = digitos(trim(s.getCpf()));
        Paciente pac = null;
        String via = null;
        if (!codPac.isEmpty()) {
            pac = pacienteRepository.findByCodigoIntegracao(codPac).orElse(null);
            via = pac != null ? "código de integração" : null;
        }
        if (pac == null && cpf.length() == 11) {
            pac = pacienteRepository.findByCpf(cpf).orElse(null);
            via = pac != null ? "CPF" : null;
        }
        linhas.add(pac != null ? "Paciente: OK — " + pac.getNome() + " (encontrado por " + via + ")."
                : configService.criar()
                        ? "Paciente: não encontrado por código de integração/CPF — será cadastrado na importação "
                                + "(cadastro automático HABILITADO)."
                        : "Paciente: não encontrado por código de integração/CPF — cadastre o paciente "
                                + "(cadastro automático DESABILITADO).");

        Optional<LocalDateTime> dataHora = parseDataHora(s);
        linhas.add(dataHora.map(d -> "Data/hora: OK — " + d.format(DATA_HORA_FMT) + ".")
                .orElse("Data/hora: inválida ou ausente (DATA_AGENDA / HOR_INI)."));

        Long procId = configService.procedimentoPadraoId();
        Procedimento proc = procId == null ? null : procedimentoRepository.findById(procId).orElse(null);
        linhas.add(proc != null ? "Procedimento (padrão SIRESP): OK — " + proc.getNome() + "."
                : "Procedimento: defina o Procedimento padrão do SIRESP nas Configurações da tela.");

        return new Avaliacao(esp, uni, prof, pac, dataHora.orElse(null), proc, linhas);
    }

    /** Texto inicial do diagnóstico (sem criar nada) — usado para popular o log na importação. */
    public String logInicial(Siresp s) {
        return montarLog(avaliar(s), null);
    }

    /**
     * Recalcula o diagnóstico e CRIA o agendamento se estiver tudo completo e ainda não houver um para este
     * registro. Deduplica por {@code ID_AGE_CONSULTA_HOR} (não cria 2 agendamentos para a mesma consulta se o XML
     * for reimportado). Atualiza o log do registro e devolve o registro já salvo. Não cria nada pela metade.
     */
    public Siresp processarRegistro(Long sirespId, Long usuarioId) {
        Siresp s = repository.findById(sirespId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));

        // Regra de negócio: registro já agendado está CONCLUÍDO — não reprocessa (o front também bloqueia o botão).
        if (s.getAgendamentoId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registro já agendado (agendamento #" + s.getAgendamentoId() + "); não é possível reprocessar.");
        }

        Avaliacao a = avaliar(s);

        // Falta informação: não cria; mostra o que falta (o usuário corrige os códigos e reprocessa).
        if (!a.completo()) {
            s.setLogIntegracao(montarLog(a, null));
            return repository.save(s);
        }

        // Dedup em dois níveis para não criar 2 agendamentos para a mesma consulta ao reimportar:
        // (1) pelo ID_AGE_CONSULTA_HOR entre registros SIRESP (id do horário no CROSS); e
        // (2) fallback pela CHAVE NATURAL do agendamento (paciente + profissional + data/hora) — cobre o caso de
        //     ID_AGE_CONSULTA_HOR vazio e acha um agendamento "órfão" (criado mas sem vínculo gravado no registro).
        Long agendamentoId = null;
        String chave = trim(s.getIdAgeConsultaHor());
        if (!chave.isEmpty()) {
            agendamentoId = repository.findFirstByIdAgeConsultaHorAndAgendamentoIdIsNotNull(chave)
                    .map(Siresp::getAgendamentoId).orElse(null);
        }
        if (agendamentoId == null) {
            agendamentoId = agendamentoRepository
                    .findFirstByPacienteAndAgenda_ProfissionalSaudeAndDataHora(a.paciente(), a.profissional(), a.dataHora())
                    .map(Horario::getId).orElse(null);
        }

        Horario novo = null;
        String nota;
        if (agendamentoId != null) {
            nota = "Agendamento #" + agendamentoId + " já existente para esta consulta (não duplicado).";
        } else {
            novo = criar(a, usuarioId);
            agendamentoId = novo.getId();
            nota = "Agendamento #" + agendamentoId + " criado.";
        }

        s.setAgendamentoId(agendamentoId);
        s.setLogIntegracao(montarLog(a, nota));
        Siresp salvo = repository.save(s);

        // Notifica o paciente/responsáveis (push + rastreio de entrega) — FORA de transação, como o cadastro manual.
        if (novo != null) {
            entregaService.notificarNovoAgendamento(novo);
        }
        return salvo;
    }

    /** Cria a Agenda (slot) + o Horário (marcação, status inicial AGUARDANDO_CONFIRMACAO_PACIENTE) + 1ª linha do log. */
    private Horario criar(Avaliacao a, Long usuarioId) {
        Agenda agenda = new Agenda();
        agenda.setData(a.dataHora().toLocalDate());
        agenda.setEspecialidade(a.especialidade());
        agenda.setProfissionalSaude(a.profissional());
        agenda.setProcedimento(a.procedimento());
        agenda.setUnidadeSaude(a.unidade());
        agenda = agendaRepository.save(agenda);

        Horario ag = new Horario();
        ag.setAgenda(agenda);
        ag.setDataHora(a.dataHora());
        ag.setPaciente(a.paciente());
        ag.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Horario salvo = agendamentoRepository.save(ag);
        agendamentoLogService.registrarDaUnidade(salvo, null, StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE,
                usuarioId);
        return salvo;
    }

    private String montarLog(Avaliacao a, String nota) {
        StringBuilder sb = new StringBuilder(a.completo()
                ? "Tudo OK — especialidade, unidade, profissional, paciente, data/hora e procedimento encontrados."
                : String.join("\n", a.linhas()));
        if (nota != null && !nota.isBlank()) {
            sb.append("\n").append(nota);
        }
        // Carimbo do último processamento (fica no fim para não quebrar a detecção do "Tudo OK" no início).
        sb.append("\nProcessado em ").append(LocalDateTime.now().format(DATA_HORA_FMT)).append(".");
        return sb.toString();
    }

    /** Combina DATA_AGENDA + HOR_INI em LocalDateTime (hora local BR). Vazio/ inválido/ data-sentinela → empty. */
    private Optional<LocalDateTime> parseDataHora(Siresp s) {
        LocalDate data = parseData(trim(s.getDataAgenda()));
        LocalTime hora = parseHora(trim(s.getHorIni()));
        if (data == null || hora == null || data.getYear() < 1900) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(data, hora));
    }

    private static LocalDate parseData(String v) {
        if (v.isEmpty()) {
            return null;
        }
        String dataParte = v.contains("T") ? v.substring(0, v.indexOf('T')) : v;
        for (DateTimeFormatter f : DATAS) {
            try {
                return LocalDate.parse(dataParte, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalTime parseHora(String v) {
        if (v.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : HORAS) {
            try {
                return LocalTime.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String digitos(String v) {
        return v.replaceAll("\\D", "");
    }
}
