package com.example.pop.agendaimportacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.configuracaoagenda.ConfiguracaoAgendaRepository;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Fase 1 da importação de agenda por Excel: PARSEIA (via {@link AgendaPlanilhaParser}) e VALIDA cada campo,
 * LOCALIZANDO os cadastros por ID ou CÓDIGO — apenas CONSULTA, nada é criado nem gravado. Devolve o
 * {@link AgendaImportPreviewResponse} com o que foi encontrado + os erros para o cliente corrigir a planilha.
 *
 * <p>Para não haver colisão (um mesmo número pode ser id de um registro e código de outro), cada cadastro tem
 * colunas SEPARADAS por tipo de identificador e o cliente preenche <b>exatamente uma</b>:
 * <ul>
 *   <li>Profissional / Especialidade: <b>id interno</b> OU <b>código de integração</b>.</li>
 *   <li>Configuração da Agenda: só <b>id interno</b> (a entidade não tem código de integração).</li>
 *   <li>Paciente: <b>id interno</b> OU <b>prontuário</b> OU <b>código de integração</b>.</li>
 *   <li>Unidade executante: não vem da planilha — é a do <b>usuário logado</b> ({@code unidadeId}).</li>
 *   <li>Status não existe — toda marcação entra como "Aguardando confirmação do paciente".</li>
 * </ul>
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

    private final AgendaPlanilhaParser parser;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ConfiguracaoAgendaRepository configuracaoAgendaRepository;
    private final UnidadeRepository unidadeRepository;
    private final PacienteRepository pacienteRepository;

    public AgendaImportacaoService(AgendaPlanilhaParser parser,
            ProfissionalSaudeRepository profissionalRepository,
            EspecialidadeRepository especialidadeRepository,
            ConfiguracaoAgendaRepository configuracaoAgendaRepository,
            UnidadeRepository unidadeRepository,
            PacienteRepository pacienteRepository) {
        this.parser = parser;
        this.profissionalRepository = profissionalRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.configuracaoAgendaRepository = configuracaoAgendaRepository;
        this.unidadeRepository = unidadeRepository;
        this.pacienteRepository = pacienteRepository;
    }

    /** Referência de cadastro para casar por id/código (id só para a Fase 2). {@code codigo} nulo = sem código. */
    private record RefCadastro(Long id, String nome, String codigo) {
    }

    /**
     * @param unidadeId unidade do usuário logado (vem do token/sessão no front) — é a unidade executante da agenda.
     */
    @Transactional(readOnly = true)
    public AgendaImportPreviewResponse preview(byte[] bytes, String arquivo, Long unidadeId) {
        AgendaPlanilhaParser.Bruta bruta = parser.parse(bytes);
        Map<String, String> a = bruta.agenda();

        List<RefCadastro> profissionais = profissionalRepository.findAll().stream()
                .map(p -> new RefCadastro(p.getId(), p.getNome(), p.getCodigoIntegracao())).toList();
        List<RefCadastro> especialidades = especialidadeRepository.findAll().stream()
                .map(e -> new RefCadastro(e.getId(), e.getNome(), e.getCodigoIntegracao())).toList();
        // ConfiguracaoAgenda não tem código de integração — só id.
        List<RefCadastro> configuracoes = configuracaoAgendaRepository.findAll().stream()
                .map(c -> new RefCadastro(c.getId(), c.getNome(), null)).toList();

        AgendaPreview agenda = new AgendaPreview(
                resolverData(a.get("data")),
                resolverIdOuCodigo(a.get("profissionalId"), a.get("profissionalCodigo"), "Profissional", profissionais),
                resolverIdOuCodigo(a.get("especialidadeId"), a.get("especialidadeCodigo"), "Especialidade", especialidades),
                resolverSomentePorId(a.get("configId"), "Configuração da Agenda", configuracoes),
                resolverUnidadeDoUsuario(unidadeId),
                CampoPreview.ok(a.getOrDefault("nome", ""))); // nome é opcional (rótulo livre)

        List<HorarioPreview> horarios = new ArrayList<>();
        for (AgendaPlanilhaParser.LinhaBruta l : bruta.horarios()) {
            horarios.add(resolverHorario(l));
        }

        int total = contarErros(agenda) + horarios.stream().mapToInt(AgendaImportacaoService::contarErros).sum();
        return new AgendaImportPreviewResponse(arquivo, agenda, horarios, horarios.size(), total);
    }

    // ---------------- Resolução de campos ----------------

    /** Profissional/Especialidade: exatamente UM entre id interno e código de integração. */
    private CampoPreview resolverIdOuCodigo(String idVal, String codigoVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        String cod = trim(codigoVal);
        boolean temId = !id.isEmpty();
        boolean temCod = !cod.isEmpty();
        if (!temId && !temCod) {
            return CampoPreview.erro("", rotulo + ": informe o id interno OU o código de integração.");
        }
        if (temId && temCod) {
            return CampoPreview.erro("id: " + id + " · código: " + cod,
                    rotulo + ": preencha apenas um (id interno OU código).");
        }
        if (temCod) {
            for (RefCadastro r : refs) {
                if (r.codigo() != null && !r.codigo().isBlank() && r.codigo().trim().equalsIgnoreCase(cod)) {
                    return CampoPreview.ok("código: " + cod, r.nome());
                }
            }
            return CampoPreview.erro("código: " + cod, rotulo + ": código de integração não encontrado.");
        }
        return porId(id, rotulo, refs);
    }

    /** Configuração da Agenda: só id interno (não há código de integração na entidade). */
    private CampoPreview resolverSomentePorId(String idVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        if (id.isEmpty()) {
            return CampoPreview.erro("", rotulo + ": informe o id interno.");
        }
        return porId(id, rotulo, refs);
    }

    private CampoPreview porId(String id, String rotulo, List<RefCadastro> refs) {
        Long idn = parseLong(id);
        if (idn == null) {
            return CampoPreview.erro("id: " + id, rotulo + ": id interno inválido.");
        }
        for (RefCadastro r : refs) {
            if (idn.equals(r.id())) {
                return CampoPreview.ok("id: " + id, r.nome());
            }
        }
        return CampoPreview.erro("id: " + id, rotulo + ": id interno não encontrado.");
    }

    /** Unidade executante = a do usuário logado (não vem da planilha). */
    private CampoPreview resolverUnidadeDoUsuario(Long unidadeId) {
        if (unidadeId == null) {
            return CampoPreview.erro("", "Unidade do usuário logado não identificada.");
        }
        return unidadeRepository.findById(unidadeId)
                .map(u -> CampoPreview.ok(u.getNome(), "unidade do usuário logado"))
                .orElse(CampoPreview.erro(String.valueOf(unidadeId), "Unidade do usuário logado não encontrada."));
    }

    private HorarioPreview resolverHorario(AgendaPlanilhaParser.LinhaBruta l) {
        CampoPreview paciente = resolverPaciente(l.pacienteId(), l.pacienteProntuario(), l.pacienteCodigo());
        CampoPreview inicio = resolverHora(l.inicio(), "Hora início", true);
        CampoPreview fim = resolverHora(l.fim(), "Hora fim", false);
        // Coerência: fim depois do início (só quando ambos são válidos).
        if (!inicio.erro() && !fim.erro() && !l.fim().isBlank()) {
            LocalTime i = parseHora(l.inicio());
            LocalTime f = parseHora(l.fim());
            if (i != null && f != null && !f.isAfter(i)) {
                fim = CampoPreview.erro(l.fim(), "Hora fim deve ser após a hora início.");
            }
        }
        return new HorarioPreview(l.linhaExcel(), paciente, inicio, fim);
    }

    /** Paciente: exatamente UM entre id interno, prontuário e código de integração. */
    private CampoPreview resolverPaciente(String idVal, String prontVal, String codVal) {
        String id = trim(idVal);
        String pront = trim(prontVal);
        String cod = trim(codVal);
        int preenchidos = (id.isEmpty() ? 0 : 1) + (pront.isEmpty() ? 0 : 1) + (cod.isEmpty() ? 0 : 1);
        if (preenchidos == 0) {
            return CampoPreview.erro("", "Paciente: informe id interno, prontuário OU código de integração.");
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
            return CampoPreview.erro(String.join(" · ", partes), "Paciente: preencha apenas um identificador.");
        }
        if (!cod.isEmpty()) {
            return pacienteRepository.findByCodigoIntegracao(cod)
                    .map(p -> CampoPreview.ok("código: " + cod, p.getNome()))
                    .orElse(CampoPreview.erro("código: " + cod, "Paciente: código de integração não encontrado."));
        }
        if (!pront.isEmpty()) {
            return pacienteRepository.findByProntuario(pront)
                    .map(p -> CampoPreview.ok("prontuário: " + pront, p.getNome()))
                    .orElse(CampoPreview.erro("prontuário: " + pront, "Paciente: prontuário não encontrado."));
        }
        Long idn = parseLong(id);
        if (idn == null) {
            return CampoPreview.erro("id: " + id, "Paciente: id interno inválido.");
        }
        return pacienteRepository.findById(idn)
                .map(p -> CampoPreview.ok("id: " + id, p.getNome()))
                .orElse(CampoPreview.erro("id: " + id, "Paciente: id interno não encontrado."));
    }

    private CampoPreview resolverData(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return CampoPreview.erro("", "Data é obrigatória.");
        }
        LocalDate data = parseData(v);
        return data == null
                ? CampoPreview.erro(valor, "Data inválida (use dd/mm/aaaa).")
                : CampoPreview.ok(valor, data.format(DATA_SAIDA));
    }

    private CampoPreview resolverHora(String valor, String rotulo, boolean obrigatorio) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return obrigatorio ? CampoPreview.erro("", rotulo + " é obrigatória.") : CampoPreview.ok("");
        }
        LocalTime hora = parseHora(v);
        return hora == null
                ? CampoPreview.erro(valor, rotulo + " inválida (use HH:mm).")
                : CampoPreview.ok(valor, hora.format(HORA_ACEITAS.get(0)));
    }

    // ---------------- Parse de data/hora ----------------

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

    // ---------------- Helpers ----------------

    private static int contarErros(AgendaPreview a) {
        return conta(a.data(), a.profissional(), a.especialidade(), a.configuracaoAgenda(), a.unidade(), a.nome());
    }

    private static int contarErros(HorarioPreview h) {
        return conta(h.paciente(), h.horaInicio(), h.horaFim());
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
