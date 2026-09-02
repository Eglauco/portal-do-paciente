package com.example.pop.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.chat.ChatRepository;
import com.example.pop.chat.MensagemRepository;
import com.example.pop.chat.RemetenteMensagem;
import com.example.pop.chat.StatusChat;
import com.example.pop.nps.NpsRepository;
import com.example.pop.nps.StatusNps;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.sau.AutorManifestacao;
import com.example.pop.sau.ManifestacaoMensagemRepository;
import com.example.pop.sau.ManifestacaoRepository;
import com.example.pop.sau.StatusManifestacao;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Agregações para os dashboards do back-office. Tudo é opcionalmente filtrado por
 * {@code unidadeId} (a unidade ativa do gestor) e por uma janela de N dias. Datas
 * usam o fuso America/Sao_Paulo — os timestamps são gravados em horário de Brasília
 * (LocalDateTime sem fuso), então a janela precisa ser calculada no mesmo referencial.
 */
@Service
public class DashboardService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int TOP = 6;

    private final AgendamentoRepository agendamentoRepository;
    private final ChatRepository chatRepository;
    private final MensagemRepository mensagemRepository;
    private final ManifestacaoRepository manifestacaoRepository;
    private final ManifestacaoMensagemRepository manifestacaoMensagemRepository;
    private final NpsRepository npsRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    public DashboardService(AgendamentoRepository agendamentoRepository, ChatRepository chatRepository,
            MensagemRepository mensagemRepository, ManifestacaoRepository manifestacaoRepository,
            ManifestacaoMensagemRepository manifestacaoMensagemRepository, NpsRepository npsRepository,
            PacienteRepository pacienteRepository, UsuarioRepository usuarioRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.chatRepository = chatRepository;
        this.mensagemRepository = mensagemRepository;
        this.manifestacaoRepository = manifestacaoRepository;
        this.manifestacaoMensagemRepository = manifestacaoMensagemRepository;
        this.npsRepository = npsRepository;
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Janela [início, fim] do período atual e do período imediatamente anterior. */
    private record Janela(LocalDateTime inicio, LocalDateTime fim, LocalDateTime inicioAnterior) {
    }

    private Janela janela(int dias) {
        // Alinhado ao início do dia: a janela cobre exatamente N dias-calendário
        // (o 1º dia inteiro; o último é "hoje" em andamento), então a série tem N
        // baldes sem um 1º ponto parcial. O período anterior tem os N dias antes.
        LocalDate hoje = LocalDate.now(FUSO);
        LocalDateTime fim = LocalDateTime.now(FUSO);
        LocalDateTime inicio = hoje.minusDays(dias - 1L).atStartOfDay();
        LocalDateTime inicioAnterior = hoje.minusDays(2L * dias - 1).atStartOfDay();
        return new Janela(inicio, fim, inicioAnterior);
    }

    // ===================== GERAL =====================

    public GeralDashboard geral(Long unidadeId, int dias) {
        Janela j = janela(dias);
        Map<StatusAgendamento, Long> ag = contagens(agendamentoRepository.agruparPorStatus(unidadeId, j.inicio(), j.fim()));
        long agTotal = soma(ag);
        long presencas = ag.getOrDefault(StatusAgendamento.PRESENCA_PACIENTE, 0L);
        long faltas = ag.getOrDefault(StatusAgendamento.FALTA_PACIENTE, 0L);
        long anterior = agendamentoRepository.contarPeriodo(unidadeId, j.inicioAnterior(), j.inicio());

        Map<StatusChat, Long> chats = contagens(chatRepository.agruparPorStatus(unidadeId));
        long chatsAbertos = soma(chats) - chats.getOrDefault(StatusChat.RESOLVIDO, 0L);
        long chatsPeriodo = chatRepository.contarCriadosPeriodo(unidadeId, j.inicio(), j.fim());

        Map<StatusManifestacao, Long> sau = contagens(manifestacaoRepository.agruparPorStatus(unidadeId));
        long sauAbertas = soma(sau) - sau.getOrDefault(StatusManifestacao.FECHADA, 0L);
        long sauPeriodo = manifestacaoRepository.contarCriadasPeriodo(unidadeId, j.inicio(), j.fim());

        Map<StatusNps, Long> nps = contagens(npsRepository.agruparPorStatus(unidadeId, j.inicio(), j.fim()));
        long npsRespondidos = nps.getOrDefault(StatusNps.RESPONDIDO, 0L);
        Double npsMedia = round1(npsRepository.mediaGeral(unidadeId, j.inicio(), j.fim()));

        return new GeralDashboard(
                dias,
                agTotal, anterior,
                presencas, faltas, taxa(presencas, presencas + faltas),
                chatsAbertos, chatsPeriodo,
                sauAbertas, sauPeriodo,
                npsRespondidos, npsMedia,
                pacienteRepository.countByDispositivoAtivoIsNotNull(),
                pacienteRepository.countByAtivoTrue(),
                pacienteRepository.count(),
                serieDiaria(agendamentoRepository.serieDiaria(unidadeId, j.inicio(), j.fim()), j),
                fatias(StatusAgendamento.values(), ag, StatusAgendamento::getDescricao));
    }

    // ===================== AGENDAMENTO =====================

    public AgendamentoDashboard agendamentos(Long unidadeId, int dias) {
        Janela j = janela(dias);
        Map<StatusAgendamento, Long> ag = contagens(agendamentoRepository.agruparPorStatus(unidadeId, j.inicio(), j.fim()));
        long total = soma(ag);
        long aguardando = ag.getOrDefault(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, 0L);
        long confirmados = ag.getOrDefault(StatusAgendamento.PACIENTE_CONFIRMOU, 0L);
        long presencas = ag.getOrDefault(StatusAgendamento.PRESENCA_PACIENTE, 0L);
        long faltas = ag.getOrDefault(StatusAgendamento.FALTA_PACIENTE, 0L);
        long cancUnidade = ag.getOrDefault(StatusAgendamento.CANCELADO_PELA_UNIDADE, 0L);
        long cancPaciente = ag.getOrDefault(StatusAgendamento.CANCELADO_PELO_PACIENTE, 0L);
        long anterior = agendamentoRepository.contarPeriodo(unidadeId, j.inicioAnterior(), j.inicio());
        LocalDateTime agora = LocalDateTime.now(FUSO);
        long proximos = agendamentoRepository.contarEntre(unidadeId, agora, agora.plusDays(7));

        return new AgendamentoDashboard(
                dias,
                total, anterior,
                aguardando, confirmados, presencas, faltas, cancUnidade, cancPaciente,
                taxa(presencas, presencas + faltas),
                taxa(confirmados + presencas, total),
                taxa(cancUnidade + cancPaciente, total),
                proximos,
                serieDiaria(agendamentoRepository.serieDiaria(unidadeId, j.inicio(), j.fim()), j),
                fatias(StatusAgendamento.values(), ag, StatusAgendamento::getDescricao),
                itens(agendamentoRepository.topProcedimentos(unidadeId, j.inicio(), j.fim())),
                itens(agendamentoRepository.topProfissionais(unidadeId, j.inicio(), j.fim())),
                itens(agendamentoRepository.porEspecialidade(unidadeId, j.inicio(), j.fim())),
                itens(agendamentoRepository.agruparMotivosFalta(unidadeId, j.inicio(), j.fim())));
    }

    // ===================== CHAT =====================

    public ChatDashboard chats(Long unidadeId, int dias) {
        Janela j = janela(dias);
        Map<StatusChat, Long> st = contagens(chatRepository.agruparPorStatus(unidadeId));
        long naoLidas = st.getOrDefault(StatusChat.NAO_LIDA, 0L);
        long aguardando = st.getOrDefault(StatusChat.AGUARDANDO_RESPOSTA, 0L);
        long emAtendimento = st.getOrDefault(StatusChat.EM_ATENDIMENTO, 0L);
        long resolvidas = st.getOrDefault(StatusChat.RESOLVIDO, 0L);
        long totalAtual = soma(st);
        long abertas = totalAtual - resolvidas;

        long conversasPeriodo = chatRepository.contarCriadosPeriodo(unidadeId, j.inicio(), j.fim());
        long anterior = chatRepository.contarCriadosPeriodo(unidadeId, j.inicioAnterior(), j.inicio());

        Map<RemetenteMensagem, Long> rem = contagens(
                mensagemRepository.agruparPorRemetentePeriodo(unidadeId, j.inicio(), j.fim()));
        long msgPaciente = rem.getOrDefault(RemetenteMensagem.PACIENTE, 0L);
        long msgUnidade = rem.getOrDefault(RemetenteMensagem.UNIDADE, 0L);

        return new ChatDashboard(
                dias,
                conversasPeriodo, anterior,
                naoLidas, aguardando, emAtendimento, resolvidas, abertas,
                chatRepository.contarSemResponsavel(unidadeId),
                msgPaciente + msgUnidade, msgPaciente, msgUnidade,
                taxa(resolvidas, totalAtual),
                serieDiaria(chatRepository.serieCriacao(unidadeId, j.inicio(), j.fim()), j),
                fatias(StatusChat.values(), st, StatusChat::getDescricao),
                itens(chatRepository.cargaPorAtendente(unidadeId)));
    }

    // ===================== SAU =====================

    public SauDashboard sau(Long unidadeId, int dias) {
        Janela j = janela(dias);
        Map<StatusManifestacao, Long> st = contagens(manifestacaoRepository.agruparPorStatus(unidadeId));
        long aguardandoSau = st.getOrDefault(StatusManifestacao.AGUARDANDO_SAU, 0L);
        long aguardandoPac = st.getOrDefault(StatusManifestacao.AGUARDANDO_PACIENTE, 0L);
        long fechadas = st.getOrDefault(StatusManifestacao.FECHADA, 0L);
        long totalAtual = soma(st);
        long abertas = totalAtual - fechadas;

        long total = manifestacaoRepository.contarCriadasPeriodo(unidadeId, j.inicio(), j.fim());
        long anterior = manifestacaoRepository.contarCriadasPeriodo(unidadeId, j.inicioAnterior(), j.inicio());

        Map<AutorManifestacao, Long> aut = contagens(
                manifestacaoMensagemRepository.agruparPorAutorPeriodo(unidadeId, j.inicio(), j.fim()));

        return new SauDashboard(
                dias,
                total, anterior,
                aguardandoSau, aguardandoPac, fechadas, abertas,
                taxa(fechadas, totalAtual),
                aut.getOrDefault(AutorManifestacao.PACIENTE, 0L), aut.getOrDefault(AutorManifestacao.SAU, 0L),
                serieDiaria(manifestacaoRepository.serieCriacao(unidadeId, j.inicio(), j.fim()), j),
                fatias(StatusManifestacao.values(), st, StatusManifestacao::getDescricao),
                itens(manifestacaoRepository.porTipo(unidadeId, j.inicio(), j.fim())),
                itens(manifestacaoMensagemRepository.cargaPorAtendente(unidadeId, j.inicio(), j.fim())));
    }

    // ===================== NPS =====================

    public NpsDashboard nps(Long unidadeId, int dias) {
        Janela j = janela(dias);
        Map<StatusNps, Long> st = contagens(npsRepository.agruparPorStatus(unidadeId, j.inicio(), j.fim()));
        long gerados = soma(st);
        long respondidos = st.getOrDefault(StatusNps.RESPONDIDO, 0L);
        long pendentes = st.getOrDefault(StatusNps.PENDENTE, 0L);
        long expirados = st.getOrDefault(StatusNps.EXPIRADO, 0L);
        long anterior = npsGerados(unidadeId, j.inicioAnterior(), j.inicio());

        List<Double> medias = npsRepository.mediasRespondidasPeriodo(unidadeId, j.inicio(), j.fim());
        long satisfeitos = medias.stream().filter(m -> m >= 4.0).count();
        long neutros = medias.stream().filter(m -> m >= 3.0 && m < 4.0).count();
        long insatisfeitos = medias.stream().filter(m -> m < 3.0).count();

        List<MediaCategoria> porCategoria = npsRepository.mediaPorCategoria(unidadeId, j.inicio(), j.fim()).stream()
                .map(r -> new MediaCategoria((String) r[0], round1(((Number) r[1]).doubleValue()),
                        ((Number) r[2]).longValue()))
                .toList();

        Map<Integer, Long> notas = new HashMap<>();
        for (Object[] r : npsRepository.distribuicaoNotas(unidadeId, j.inicio(), j.fim())) {
            notas.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        }
        List<ItemContagem> distribuicao = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            distribuicao.add(new ItemContagem(n + (n == 1 ? " estrela" : " estrelas"), notas.getOrDefault(n, 0L)));
        }

        return new NpsDashboard(
                dias,
                gerados, anterior,
                respondidos, pendentes, expirados,
                taxa(respondidos, gerados), round1(npsRepository.mediaGeral(unidadeId, j.inicio(), j.fim())),
                satisfeitos, neutros, insatisfeitos,
                serieDiaria(npsRepository.serieCriacao(unidadeId, j.inicio(), j.fim()), j),
                fatias(StatusNps.values(), st, StatusNps::getDescricao),
                porCategoria, distribuicao);
    }

    private long npsGerados(Long unidadeId, LocalDateTime inicio, LocalDateTime fim) {
        return soma(contagens(npsRepository.agruparPorStatus(unidadeId, inicio, fim)));
    }

    // ===================== Helpers =====================

    /** Object[]{enum, Long} -> mapa. */
    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> Map<E, Long> contagens(List<Object[]> rows) {
        Map<E, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((E) r[0], ((Number) r[1]).longValue());
        }
        return m;
    }

    private long soma(Map<?, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Lista de fatias na ordem dos valores do enum (inclui zeros, para cores estáveis). */
    private <E extends Enum<E>> List<Fatia> fatias(E[] valores, Map<E, Long> contagens, Function<E, String> rotulo) {
        return Arrays.stream(valores)
                .map(v -> new Fatia(v.name(), rotulo.apply(v), contagens.getOrDefault(v, 0L)))
                .toList();
    }

    /** Object[]{String nome, Long qtd} -> top N itens de contagem. */
    private List<ItemContagem> itens(List<Object[]> rows) {
        return rows.stream()
                .limit(TOP)
                .map(r -> new ItemContagem((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    /** Monta a série a partir das linhas (data, contagem) já agrupadas no banco, preenchendo dias vazios. */
    private List<SerieDiaria> serieDiaria(List<Object[]> linhas, Janela j) {
        Map<LocalDate, Long> porDia = new HashMap<>();
        for (Object[] r : linhas) {
            porDia.put(comoData(r[0]), ((Number) r[1]).longValue());
        }
        List<SerieDiaria> serie = new ArrayList<>();
        LocalDate ini = j.inicio().toLocalDate();
        LocalDate fim = j.fim().toLocalDate();
        for (LocalDate d = ini; !d.isAfter(fim); d = d.plusDays(1)) {
            serie.add(new SerieDiaria(d.toString(), porDia.getOrDefault(d, 0L)));
        }
        return serie;
    }

    /** Coage o resultado de cast(... as date) para LocalDate (o driver pode devolver java.sql.Date). */
    private LocalDate comoData(Object valor) {
        if (valor instanceof LocalDate d) return d;
        if (valor instanceof java.sql.Date d) return d.toLocalDate();
        if (valor instanceof java.util.Date d) return d.toInstant().atZone(FUSO).toLocalDate();
        return LocalDate.parse(valor.toString());
    }

    /** Percentual (0–100) com 1 casa; 0 quando o total é 0. */
    private double taxa(long parte, long total) {
        return total == 0 ? 0.0 : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private Double round1(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}
