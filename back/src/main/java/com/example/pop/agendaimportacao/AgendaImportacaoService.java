package com.example.pop.agendaimportacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agenda;
import com.example.pop.agendamento.AgendaRepository;
import com.example.pop.agendamento.Horario;
import com.example.pop.agendamento.HorarioEntregaService;
import com.example.pop.agendamento.HorarioLogService;
import com.example.pop.agendamento.HorarioRepository;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.configuracaoagenda.ConfiguracaoAgendaRepository;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Importação de agenda por Excel. PARSEIA (via {@link AgendaPlanilhaParser}) e LOCALIZA os cadastros por
 * ID ou CÓDIGO (nunca por nome). Duas operações:
 * <ul>
 *   <li>{@link #preview} (Fase 1): devolve o {@link AgendaImportPreviewResponse} com o que foi encontrado +
 *       os erros — não grava nada.</li>
 *   <li>{@link #persistir} (Fase 2): re-valida no servidor e, se não houver erro, CRIA a Agenda + os Horários
 *       numa transação; a notificação ao paciente roda DEPOIS via {@link #notificarImportados}.</li>
 * </ul>
 *
 * <p>Cada cadastro tem colunas separadas por tipo de identificador e o cliente preenche exatamente uma:
 * Profissional/Especialidade por id interno OU código; Configuração da Agenda só por id; Paciente por id interno
 * OU prontuário OU código. A unidade executante é a do usuário logado ({@code unidadeId}). O status nasce sempre
 * "Aguardando confirmação do paciente".
 */
@Service
public class AgendaImportacaoService {

    private static final DateTimeFormatter DATA_SAIDA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<DateTimeFormatter> DATA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    private static final List<DateTimeFormatter> HORA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"));
    private static final StatusAgendamento STATUS_INICIAL = StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE;

    private final AgendaPlanilhaParser parser;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ConfiguracaoAgendaRepository configuracaoAgendaRepository;
    private final UnidadeRepository unidadeRepository;
    private final PacienteRepository pacienteRepository;
    private final AgendaRepository agendaRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioLogService logService;
    private final HorarioEntregaService entregaService;

    public AgendaImportacaoService(AgendaPlanilhaParser parser,
            ProfissionalSaudeRepository profissionalRepository,
            EspecialidadeRepository especialidadeRepository,
            ConfiguracaoAgendaRepository configuracaoAgendaRepository,
            UnidadeRepository unidadeRepository,
            PacienteRepository pacienteRepository,
            AgendaRepository agendaRepository,
            HorarioRepository horarioRepository,
            HorarioLogService logService,
            HorarioEntregaService entregaService) {
        this.parser = parser;
        this.profissionalRepository = profissionalRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.configuracaoAgendaRepository = configuracaoAgendaRepository;
        this.unidadeRepository = unidadeRepository;
        this.pacienteRepository = pacienteRepository;
        this.agendaRepository = agendaRepository;
        this.horarioRepository = horarioRepository;
        this.logService = logService;
        this.entregaService = entregaService;
    }

    /** Referência de cadastro para casar por id/código. {@code codigo} nulo = sem código de integração. */
    private record RefCadastro(Long id, String nome, String codigo) {
    }

    /** Resultado de uma resolução: o que mostrar ({@code campo}) + o valor resolvido para gravar ({@code valor}). */
    private record Campo<T>(CampoPreview campo, T valor) {
    }

    /** Agenda inteira resolvida: previews + valores para gravar. */
    private record AgendaResolvida(Campo<LocalDate> data, Campo<Long> profissional, Campo<Long> especialidade,
            Campo<Long> config, Campo<Long> unidade, CampoPreview nome, List<HorarioResolvido> horarios) {

        AgendaPreview preview() {
            return new AgendaPreview(data.campo(), profissional.campo(), especialidade.campo(),
                    config.campo(), unidade.campo(), nome);
        }
    }

    private record HorarioResolvido(int linha, Campo<Long> paciente, Campo<LocalTime> inicio, Campo<LocalTime> fim) {

        HorarioPreview preview() {
            return new HorarioPreview(linha, paciente.campo(), inicio.campo(), fim.campo());
        }
    }

    /** O que a gravação devolve para o controller notificar fora da transação. */
    public record ResultadoImportacao(Long agendaId, List<Long> horarioIds) {
    }

    // ---------------- Fase 1: preview ----------------

    /** @param unidadeId unidade do usuário logado — é a unidade executante da agenda. */
    @Transactional(readOnly = true)
    public AgendaImportPreviewResponse preview(byte[] bytes, String arquivo, Long unidadeId) {
        AgendaResolvida r = resolver(bytes, unidadeId);
        List<HorarioPreview> horarios = r.horarios().stream().map(HorarioResolvido::preview).toList();
        return new AgendaImportPreviewResponse(arquivo, r.preview(), horarios, horarios.size(), totalErros(r));
    }

    // ---------------- Fase 2: gravação ----------------

    /**
     * Re-valida no servidor e, se não houver erro, CRIA a Agenda + os Horários numa transação. NÃO notifica aqui
     * (o POST à Expo roda fora de transação): o controller chama {@link #notificarImportados} depois do commit.
     */
    @Transactional
    public ResultadoImportacao persistir(byte[] bytes, Long unidadeId, Long uid) {
        AgendaResolvida r = resolver(bytes, unidadeId);
        if (totalErros(r) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Corrija os erros destacados antes de confirmar a importação.");
        }
        if (r.horarios().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A planilha não tem nenhum horário para importar.");
        }

        Agenda agenda = new Agenda();
        agenda.setData(r.data().valor());
        agenda.setProfissionalSaude(profissionalRepository.findById(r.profissional().valor())
                .orElseThrow(() -> sumiu("Profissional")));
        agenda.setEspecialidade(especialidadeRepository.findById(r.especialidade().valor())
                .orElseThrow(() -> sumiu("Especialidade")));
        agenda.setConfiguracaoAgenda(configuracaoAgendaRepository.findById(r.config().valor())
                .orElseThrow(() -> sumiu("Configuração da Agenda")));
        agenda.setUnidadeSaude(unidadeRepository.findById(r.unidade().valor())
                .orElseThrow(() -> sumiu("Unidade")));
        String nome = r.nome().valor();
        if (nome != null && !nome.isBlank()) {
            agenda.setNome(nome.trim());
        }
        Agenda agendaSalva = agendaRepository.save(agenda);

        List<Long> horarioIds = new ArrayList<>();
        for (HorarioResolvido hr : r.horarios()) {
            Horario h = new Horario();
            h.setAgenda(agendaSalva);
            h.setPaciente(pacienteRepository.findById(hr.paciente().valor())
                    .orElseThrow(() -> sumiu("Paciente")));
            h.setDataHora(r.data().valor().atTime(hr.inicio().valor()));
            h.setHoraFim(hr.fim().valor()); // opcional (pode ser null)
            h.setStatusAgendamento(STATUS_INICIAL);
            Horario salvo = horarioRepository.save(h);
            logService.registrarDaUnidade(salvo, null, STATUS_INICIAL, uid);
            horarioIds.add(salvo.getId());
        }
        return new ResultadoImportacao(agendaSalva.getId(), horarioIds);
    }

    /**
     * Notifica cada paciente do novo agendamento (push + inbox), FORA de transação e best-effort: uma falha de
     * notificação não desfaz a importação já gravada. Recarrega cada horário (associações EAGER) para o envio.
     */
    public void notificarImportados(List<Long> horarioIds) {
        for (Long id : horarioIds) {
            try {
                horarioRepository.findById(id).ifPresent(entregaService::notificarNovoAgendamento);
            } catch (RuntimeException ignorado) {
                // best-effort: não derruba o restante nem a importação já persistida
            }
        }
    }

    // ---------------- Resolução ----------------

    private AgendaResolvida resolver(byte[] bytes, Long unidadeId) {
        AgendaPlanilhaParser.Bruta bruta = parser.parse(bytes);
        Map<String, String> a = bruta.agenda();

        List<RefCadastro> profissionais = profissionalRepository.findAll().stream()
                .map(p -> new RefCadastro(p.getId(), p.getNome(), p.getCodigoIntegracao())).toList();
        List<RefCadastro> especialidades = especialidadeRepository.findAll().stream()
                .map(e -> new RefCadastro(e.getId(), e.getNome(), e.getCodigoIntegracao())).toList();
        List<RefCadastro> configuracoes = configuracaoAgendaRepository.findAll().stream()
                .map(c -> new RefCadastro(c.getId(), c.getNome(), null)).toList();

        Campo<LocalDate> data = resolverData(a.get("data"));
        Campo<Long> prof = resolverIdOuCodigo(a.get("profissionalId"), a.get("profissionalCodigo"), "Profissional", profissionais);
        Campo<Long> esp = resolverIdOuCodigo(a.get("especialidadeId"), a.get("especialidadeCodigo"), "Especialidade", especialidades);
        Campo<Long> cfg = resolverSomentePorId(a.get("configId"), "Configuração da Agenda", configuracoes);
        Campo<Long> uni = resolverUnidadeDoUsuario(unidadeId);
        CampoPreview nome = CampoPreview.ok(a.getOrDefault("nome", ""));

        List<HorarioResolvido> horarios = new ArrayList<>();
        for (AgendaPlanilhaParser.LinhaBruta l : bruta.horarios()) {
            Campo<Long> pac = resolverPaciente(l.pacienteId(), l.pacienteProntuario(), l.pacienteCodigo());
            Campo<LocalTime> inicio = resolverHora(l.inicio(), "Hora início", true);
            Campo<LocalTime> fim = resolverHora(l.fim(), "Hora fim", false);
            if (!inicio.campo().erro() && !fim.campo().erro() && inicio.valor() != null && fim.valor() != null
                    && !fim.valor().isAfter(inicio.valor())) {
                fim = erro(l.fim(), "Hora fim deve ser após a hora início.");
            }
            horarios.add(new HorarioResolvido(l.linhaExcel(), pac, inicio, fim));
        }
        return new AgendaResolvida(data, prof, esp, cfg, uni, nome, horarios);
    }

    /** Profissional/Especialidade: exatamente UM entre id interno e código de integração. */
    private Campo<Long> resolverIdOuCodigo(String idVal, String codigoVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        String cod = trim(codigoVal);
        boolean temId = !id.isEmpty();
        boolean temCod = !cod.isEmpty();
        if (!temId && !temCod) {
            return erro("", rotulo + ": informe o id interno OU o código de integração.");
        }
        if (temId && temCod) {
            return erro("id: " + id + " · código: " + cod, rotulo + ": preencha apenas um (id interno OU código).");
        }
        if (temCod) {
            for (RefCadastro r : refs) {
                if (r.codigo() != null && !r.codigo().isBlank() && r.codigo().trim().equalsIgnoreCase(cod)) {
                    return new Campo<>(CampoPreview.ok("código: " + cod, r.nome()), r.id());
                }
            }
            return erro("código: " + cod, rotulo + ": código de integração não encontrado.");
        }
        return porId(id, rotulo, refs);
    }

    /** Configuração da Agenda: só id interno (não há código de integração na entidade). */
    private Campo<Long> resolverSomentePorId(String idVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        if (id.isEmpty()) {
            return erro("", rotulo + ": informe o id interno.");
        }
        return porId(id, rotulo, refs);
    }

    private Campo<Long> porId(String id, String rotulo, List<RefCadastro> refs) {
        Long idn = parseLong(id);
        if (idn == null) {
            return erro("id: " + id, rotulo + ": id interno inválido.");
        }
        for (RefCadastro r : refs) {
            if (idn.equals(r.id())) {
                return new Campo<>(CampoPreview.ok("id: " + id, r.nome()), r.id());
            }
        }
        return erro("id: " + id, rotulo + ": id interno não encontrado.");
    }

    /** Unidade executante = a do usuário logado (não vem da planilha). */
    private Campo<Long> resolverUnidadeDoUsuario(Long unidadeId) {
        if (unidadeId == null) {
            return erro("", "Unidade do usuário logado não identificada.");
        }
        return unidadeRepository.findById(unidadeId)
                .map(u -> new Campo<>(CampoPreview.ok(u.getNome(), "unidade do usuário logado"), u.getId()))
                .orElseGet(() -> erro(String.valueOf(unidadeId), "Unidade do usuário logado não encontrada."));
    }

    /** Paciente: exatamente UM entre id interno, prontuário e código de integração. */
    private Campo<Long> resolverPaciente(String idVal, String prontVal, String codVal) {
        String id = trim(idVal);
        String pront = trim(prontVal);
        String cod = trim(codVal);
        int preenchidos = (id.isEmpty() ? 0 : 1) + (pront.isEmpty() ? 0 : 1) + (cod.isEmpty() ? 0 : 1);
        if (preenchidos == 0) {
            return erro("", "Paciente: informe id interno, prontuário OU código de integração.");
        }
        if (preenchidos > 1) {
            List<String> partes = new ArrayList<>();
            if (!id.isEmpty()) {
                partes.add("id: " + id);
            }
            if (!pront.isEmpty()) {
                partes.add("prontuário: " + pront);
            }
            if (!cod.isEmpty()) {
                partes.add("código: " + cod);
            }
            return erro(String.join(" · ", partes), "Paciente: preencha apenas um identificador.");
        }
        if (!cod.isEmpty()) {
            return pacienteRepository.findByCodigoIntegracao(cod)
                    .map(p -> new Campo<>(CampoPreview.ok("código: " + cod, p.getNome()), p.getId()))
                    .orElseGet(() -> erro("código: " + cod, "Paciente: código de integração não encontrado."));
        }
        if (!pront.isEmpty()) {
            return pacienteRepository.findByProntuario(pront)
                    .map(p -> new Campo<>(CampoPreview.ok("prontuário: " + pront, p.getNome()), p.getId()))
                    .orElseGet(() -> erro("prontuário: " + pront, "Paciente: prontuário não encontrado."));
        }
        Long idn = parseLong(id);
        if (idn == null) {
            return erro("id: " + id, "Paciente: id interno inválido.");
        }
        return pacienteRepository.findById(idn)
                .map(p -> new Campo<>(CampoPreview.ok("id: " + id, p.getNome()), p.getId()))
                .orElseGet(() -> erro("id: " + id, "Paciente: id interno não encontrado."));
    }

    private Campo<LocalDate> resolverData(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", "Data é obrigatória.");
        }
        LocalDate data = parseData(v);
        return data == null
                ? erro(valor, "Data inválida (use dd/mm/aaaa).")
                : new Campo<>(CampoPreview.ok(valor, data.format(DATA_SAIDA)), data);
    }

    private Campo<LocalTime> resolverHora(String valor, String rotulo, boolean obrigatorio) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return obrigatorio ? erro("", rotulo + " é obrigatória.") : new Campo<>(CampoPreview.ok(""), null);
        }
        LocalTime hora = parseHora(v);
        return hora == null
                ? erro(valor, rotulo + " inválida (use HH:mm).")
                : new Campo<>(CampoPreview.ok(valor, hora.format(HORA_ACEITAS.get(0))), hora);
    }

    // ---------------- Parse / helpers ----------------

    private static LocalDate parseData(String v) {
        for (DateTimeFormatter f : DATA_ACEITAS) {
            try {
                return LocalDate.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalTime parseHora(String v) {
        for (DateTimeFormatter f : HORA_ACEITAS) {
            try {
                return LocalTime.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static Long parseLong(String v) {
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /** Erro de resolução: campo destacado + sem valor para gravar. */
    private static <T> Campo<T> erro(String valor, String mensagem) {
        return new Campo<>(CampoPreview.erro(valor, mensagem), null);
    }

    private static ResponseStatusException sumiu(String rotulo) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                rotulo + " não está mais disponível. Reimporte para revalidar.");
    }

    private static int totalErros(AgendaResolvida r) {
        int n = conta(r.data().campo(), r.profissional().campo(), r.especialidade().campo(),
                r.config().campo(), r.unidade().campo(), r.nome());
        for (HorarioResolvido h : r.horarios()) {
            n += conta(h.paciente().campo(), h.inicio().campo(), h.fim().campo());
        }
        return n;
    }

    private static int conta(CampoPreview... campos) {
        int n = 0;
        for (CampoPreview c : campos) {
            if (c != null && c.erro()) {
                n++;
            }
        }
        return n;
    }
}
