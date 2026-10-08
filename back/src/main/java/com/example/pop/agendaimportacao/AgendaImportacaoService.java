package com.example.pop.agendaimportacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.configuracaoagenda.ConfiguracaoAgendaRepository;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Fase 1 da importação de agenda por Excel: dado o arquivo, PARSEIA (via {@link AgendaPlanilhaParser}) e
 * VALIDA cada campo, LOCALIZANDO os cadastros por ID ou CÓDIGO (nunca por nome, p/ não haver divergência) —
 * apenas CONSULTA, nada é criado nem gravado. Devolve o {@link AgendaImportPreviewResponse} com o que foi
 * encontrado + os erros para o cliente corrigir a planilha. A gravação fica para a Fase 2.
 *
 * <p>Regras desta planilha (toda por código):
 * <ul>
 *   <li>Profissional e Especialidade: por <b>id interno OU código de integração</b> (código tem precedência).</li>
 *   <li>Configuração da Agenda ({@link com.example.pop.configuracaoagenda.ConfiguracaoAgenda}): só por <b>id
 *       interno</b> — a entidade não tem código de integração.</li>
 *   <li>Unidade executante: NÃO vem da planilha — é a unidade do <b>usuário logado</b> (param {@code unidadeId}).</li>
 *   <li>Paciente: localizado por <b>código de integração → prontuário → id interno</b> (nessa ordem).</li>
 *   <li>Status: não existe — toda marcação entra como "Aguardando confirmação do paciente".</li>
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
        // ConfiguracaoAgenda não tem código de integração — casa só por id.
        List<RefCadastro> configuracoes = configuracaoAgendaRepository.findAll().stream()
                .map(c -> new RefCadastro(c.getId(), c.getNome(), null)).toList();

        AgendaPreview agenda = new AgendaPreview(
                resolverData(a.get("data")),
                resolverPorIdOuCodigo(a.get("profissional"), "Profissional", profissionais),
                resolverPorIdOuCodigo(a.get("especialidade"), "Especialidade", especialidades),
                resolverPorIdOuCodigo(a.get("config"), "Configuração da Agenda", configuracoes),
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

    /** Casa por código de integração (precedência) e, se não houver, por id interno. Nunca por nome. */
    private CampoPreview resolverPorIdOuCodigo(String valor, String rotulo, List<RefCadastro> refs) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", rotulo + " é obrigatório(a).");
        }
        for (RefCadastro r : refs) {
            if (r.codigo() != null && !r.codigo().isBlank() && r.codigo().trim().equalsIgnoreCase(v)) {
                return CampoPreview.ok(valor, r.nome());
            }
        }
        Long id = parseLong(v);
        if (id != null) {
            for (RefCadastro r : refs) {
                if (id.equals(r.id())) {
                    return CampoPreview.ok(valor, r.nome());
                }
            }
        }
        return CampoPreview.erro(valor, rotulo + " não encontrado(a) pelo id ou código.");
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
        CampoPreview paciente = resolverPaciente(l.paciente());
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

    /** Localiza o paciente por código de integração → prontuário → id interno (nessa ordem). */
    private CampoPreview resolverPaciente(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", "Identificador do paciente é obrigatório.");
        }
        Optional<Paciente> p = pacienteRepository.findByCodigoIntegracao(v);
        if (p.isEmpty()) {
            p = pacienteRepository.findByProntuario(v);
        }
        if (p.isEmpty()) {
            Long id = parseLong(v);
            if (id != null) {
                p = pacienteRepository.findById(id);
            }
        }
        return p.map(pac -> CampoPreview.ok(valor, pac.getNome()))
                .orElse(CampoPreview.erro(valor, "Paciente não encontrado por id, prontuário ou código."));
    }

    private CampoPreview resolverData(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", "Data é obrigatória.");
        }
        LocalDate data = parseData(v);
        return data == null
                ? CampoPreview.erro(valor, "Data inválida (use dd/mm/aaaa).")
                : CampoPreview.ok(valor, data.format(DATA_SAIDA));
    }

    private CampoPreview resolverHora(String valor, String rotulo, boolean obrigatorio) {
        String v = valor == null ? "" : valor.trim();
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
