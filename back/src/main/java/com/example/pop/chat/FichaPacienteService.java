package com.example.pop.chat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.lembrete.Lembrete;
import com.example.pop.lembrete.LembreteRepository;
import com.example.pop.prontuario.ProntuarioRepository;

/**
 * Monta a "ficha" textual e compacta do paciente (próximos agendamentos + preparo/orientações +
 * prazo de cancelamento + disponibilidade de documento no prontuário) para a IA do chat usar como
 * contexto. SOMENTE LEITURA e SEMPRE do próprio paciente da conversa (nunca de terceiros — LGPD).
 * As datas usam o fuso America/São_Paulo, igual ao resto do domínio de agendamento.
 */
@Service
public class FichaPacienteService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH'h'mm", new Locale("pt", "BR"));
    /** No máximo estes agendamentos futuros na ficha (mantém o prompt curto/barato). */
    private static final int MAX_AGENDAMENTOS = 3;

    private final AgendamentoRepository agendamentoRepository;
    private final ProntuarioRepository prontuarioRepository;
    private final LembreteRepository lembreteRepository;

    public FichaPacienteService(AgendamentoRepository agendamentoRepository,
            ProntuarioRepository prontuarioRepository, LembreteRepository lembreteRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.prontuarioRepository = prontuarioRepository;
        this.lembreteRepository = lembreteRepository;
    }

    /**
     * Texto compacto com os dados do paciente NA UNIDADE da conversa para a IA (vazio nunca — sempre
     * descreve o estado). Escopado por {@code unidadeId}: nunca mistura dados de outras unidades.
     */
    @Transactional(readOnly = true)
    public String montar(Long pacienteId, Long unidadeId) {
        LocalDateTime agora = LocalDateTime.now(FUSO);
        StringBuilder sb = new StringBuilder();

        List<Agendamento> proximos = agendamentoRepository
                .findByPaciente_IdAndUnidadeSaude_IdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(pacienteId, unidadeId, agora);
        if (proximos.isEmpty()) {
            sb.append("Próximos agendamentos nesta unidade: nenhum agendamento futuro.\n");
        } else {
            sb.append("Próximos agendamentos nesta unidade:\n");
            proximos.stream().limit(MAX_AGENDAMENTOS).forEach(a -> sb.append(descreverAgendamento(a, agora)));
        }

        boolean docDisponivel = prontuarioRepository
                .search("", pacienteId, unidadeId, "", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream().findFirst()
                .map(p -> !p.getDocumentos().isEmpty())
                .orElse(false);
        sb.append("Documento/resultado mais recente no prontuário (nesta unidade): ")
                .append(docDisponivel
                        ? "disponível para o paciente ver na tela de Prontuário do app."
                        : "nenhum documento disponível ainda.")
                .append("\n");

        return sb.toString().trim();
    }

    private String descreverAgendamento(Agendamento a, LocalDateTime agora) {
        StringBuilder s = new StringBuilder("- ")
                .append(a.getProcedimento().getNome())
                .append(" em ").append(a.getDataHora().format(DATA))
                .append(" na unidade ").append(a.getUnidadeSaude().getNome());
        if (a.getProfissionalSaude() != null) {
            s.append(" com ").append(a.getProfissionalSaude().getNome());
        }
        s.append(" (status: ").append(a.getStatusAgendamento().getDescricao()).append(").");

        String preparo = a.getProcedimento().getPreparo();
        if (preparo != null && !preparo.isBlank()) {
            s.append(" Preparo: ").append(preparo.trim()).append(".");
        }

        List<Lembrete> lembretes = lembreteRepository
                .findByProcedimentoIdOrderByHorasAntecedenciaDesc(a.getProcedimento().getId());
        String orientacoes = lembretes.stream()
                .map(Lembrete::getTexto)
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("; "));
        if (!orientacoes.isBlank()) {
            s.append(" Orientações: ").append(orientacoes).append(".");
        }

        s.append(" ").append(descreverCancelamento(a, agora)).append("\n");
        return s.toString();
    }

    /** Mesma regra do cancelamento pelo app: só confirmado, e dentro de dataHora - horasCancelamento. */
    private String descreverCancelamento(Agendamento a, LocalDateTime agora) {
        if (a.getStatusAgendamento() != StatusAgendamento.PACIENTE_CONFIRMOU) {
            return "Cancelamento pelo app: só é possível quando o agendamento está confirmado pelo paciente.";
        }
        Integer horas = a.getProcedimento().getHorasCancelamento();
        if (horas == null) {
            return "Cancelamento pelo app: permitido.";
        }
        LocalDateTime prazo = a.getDataHora().minusHours(horas);
        if (agora.isAfter(prazo)) {
            return "Cancelamento pelo app: o prazo já encerrou (era até " + prazo.format(DATA) + ").";
        }
        return "Cancelamento pelo app: permitido até " + prazo.format(DATA) + ".";
    }
}
