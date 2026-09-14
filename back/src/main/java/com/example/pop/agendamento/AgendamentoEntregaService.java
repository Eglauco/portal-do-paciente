package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.paciente.ContaAppRepository;
import com.example.pop.paciente.Documentos;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.NivelAcessoResponsavel;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.push.Dispositivo;
import com.example.pop.push.DispositivoRepository;
import com.example.pop.push.PushService;

/**
 * Dispara a notificação de NOVO agendamento e RASTREIA a entrega por destinatário (Fase 1):
 * o próprio paciente + cada responsável ATIVO com acesso a Agendamentos. Para cada pessoa,
 * grava {@link EstadoEntrega} no momento do disparo e consolida um resumo no agendamento —
 * é o que dá ao admin a visão de "chegou ao destino?".
 *
 * <p>O POST à Expo roda FORA de transação (não segura conexão do pool); só a gravação
 * (linhas + resumo) entra numa transação curta. O resumo é gravado recarregando o
 * agendamento como entidade GERENCIADA — o objeto vindo do {@code criar} está destacado
 * (criar não é @Transactional e open-in-view está desligado), então um simples setter nele
 * não geraria UPDATE.
 */
@Service
public class AgendamentoEntregaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final PushService pushService;
    private final NotificacaoService notificacaoService;
    private final ResponsavelRepository responsavelRepository;
    private final ContaAppRepository contaRepository;
    private final DispositivoRepository dispositivoRepository;
    private final AgendamentoEntregaRepository entregaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final TransactionTemplate transactionTemplate;

    public AgendamentoEntregaService(PushService pushService, NotificacaoService notificacaoService,
            ResponsavelRepository responsavelRepository, ContaAppRepository contaRepository,
            DispositivoRepository dispositivoRepository, AgendamentoEntregaRepository entregaRepository,
            AgendamentoRepository agendamentoRepository, PlatformTransactionManager transactionManager) {
        this.pushService = pushService;
        this.notificacaoService = notificacaoService;
        this.responsavelRepository = responsavelRepository;
        this.contaRepository = contaRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.entregaRepository = entregaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Pessoa autorizada a receber + seus tokens de push (vazio = sem aplicativo apto). */
    private record Destinatario(TipoDestinatario tipo, Long responsavelId, String nome, String telefone,
            List<String> tokens) {
    }

    /** Cria a notificação de novo agendamento (inbox + push) e grava a entrega por destinatário. */
    public void notificarNovoAgendamento(Agendamento a) {
        Long pacienteId = a.getPaciente().getId();
        String corpo = "Consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT)
                + ". Toque para confirmar ou cancelar.";
        // Inbox (sino): gravado sempre, como antes, independentemente da entrega do push.
        notificacaoService.registrar(pacienteId, TipoNotificacao.AGENDAMENTO, "Novo agendamento", corpo, a.getId());

        List<Destinatario> destinatarios = resolverDestinatarios(a.getPaciente());

        // Um único POST para todos os tokens; mapeia token -> aceito pela Expo. FORA de transação.
        LinkedHashMap<String, Destinatario> donoDoToken = new LinkedHashMap<>();
        for (Destinatario d : destinatarios) {
            for (String token : d.tokens()) {
                donoDoToken.putIfAbsent(token, d);
            }
        }
        Map<String, Boolean> aceitoPorToken = new HashMap<>();
        Map<String, String> receiptIdPorToken = new HashMap<>();
        if (!donoDoToken.isEmpty()) {
            String titulo = comPrefixo(a.getPaciente().getNome(), "Novo agendamento");
            Map<String, Object> data = Map.of("tipo", "AGENDAMENTO", "agendamentoId", a.getId(), "pacienteId", pacienteId);
            for (PushService.ResultadoEnvio r : pushService.enviarComResultado(titulo, corpo, data,
                    List.copyOf(donoDoToken.keySet()))) {
                aceitoPorToken.put(r.token(), r.aceito());
                if (r.receiptId() != null) {
                    receiptIdPorToken.put(r.token(), r.receiptId());
                }
            }
        }

        // Monta as linhas de entrega + o resumo (em memória).
        LocalDateTime agora = LocalDateTime.now(FUSO);
        List<AgendamentoEntrega> entregas = new ArrayList<>();
        EstadoEntrega resumo = null;
        for (Destinatario d : destinatarios) {
            EstadoEntrega estado = estadoDe(d, aceitoPorToken);
            AgendamentoEntrega entrega = new AgendamentoEntrega();
            entrega.setAgendamento(a);
            entrega.setTipo(d.tipo());
            entrega.setResponsavelId(d.responsavelId());
            entrega.setNome(d.nome());
            entrega.setTelefone(Documentos.somenteDigitos(d.telefone()));
            entrega.setEstado(estado);
            // Enviada: guarda os receipt ids "ok" da pessoa para o job confirmar a entrega (Fase 2).
            if (estado == EstadoEntrega.NOTIFICACAO_ENVIADA) {
                entrega.setReceiptsPendentes(receiptsDe(d, receiptIdPorToken));
            }
            entrega.setCriadoEm(agora);
            entregas.add(entrega);
            resumo = combinar(resumo, estado);
        }
        EstadoEntrega resumoFinal = resumo;
        a.setEntregaResumo(resumoFinal); // reflete na resposta imediata do POST (a persistência é abaixo)

        // Grava as linhas + o resumo numa ÚNICA transação curta, DEPOIS do HTTP. Recarrega o
        // agendamento para tê-lo GERENCIADO — setá-lo no 'a' destacado não geraria UPDATE.
        transactionTemplate.executeWithoutResult(status -> {
            entregas.forEach(entregaRepository::save);
            agendamentoRepository.findById(a.getId())
                    .ifPresent(gerenciado -> gerenciado.setEntregaResumo(resumoFinal));
        });
    }

    /** Estado da pessoa: sem token = sem app; algum token aceito = enviada; senão = sem notificação ativa. */
    private EstadoEntrega estadoDe(Destinatario d, Map<String, Boolean> aceitoPorToken) {
        if (d.tokens().isEmpty()) {
            return EstadoEntrega.PACIENTE_SEM_APLICATIVO;
        }
        boolean algumAceito = d.tokens().stream().anyMatch(t -> aceitoPorToken.getOrDefault(t, false));
        return algumAceito ? EstadoEntrega.NOTIFICACAO_ENVIADA : EstadoEntrega.SEM_NOTIFICACAO_ATIVA;
    }

    /** Resumo "qualquer autorizado": o MELHOR resultado vence (entregue > enviada > sem notif. > sem app). */
    private EstadoEntrega combinar(EstadoEntrega atual, EstadoEntrega nova) {
        if (atual == null) {
            return nova;
        }
        for (EstadoEntrega preferido : List.of(EstadoEntrega.NOTIFICACAO_ENTREGUE,
                EstadoEntrega.NOTIFICACAO_ENVIADA, EstadoEntrega.SEM_NOTIFICACAO_ATIVA)) {
            if (atual == preferido || nova == preferido) {
                return preferido;
            }
        }
        return EstadoEntrega.PACIENTE_SEM_APLICATIVO;
    }

    /**
     * Job (Fase 2): consulta os receipts da Expo e promove NOTIFICACAO_ENVIADA → NOTIFICACAO_ENTREGUE
     * (receipt "ok") ou SEM_NOTIFICACAO_ATIVA (receipt com erro). Receipts ficam prontos ~15 min após
     * o envio e a Expo os descarta após ~24h (aí desistimos e a linha fica como "enviada").
     */
    public void resolverReceiptsPendentes() {
        LocalDateTime agora = LocalDateTime.now(FUSO);
        List<AgendamentoEntrega> pendentes = entregaRepository
                .findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
                        EstadoEntrega.NOTIFICACAO_ENVIADA, agora.minusMinutes(15));
        if (pendentes.isEmpty()) {
            return;
        }
        // HTTP FORA de transação; id ausente do mapa = ainda não pronto (tenta no próximo ciclo).
        List<String> todosIds = pendentes.stream()
                .flatMap(e -> parseIds(e.getReceiptsPendentes()).stream())
                .distinct().toList();
        Map<String, Boolean> status = pushService.consultarReceipts(todosIds);

        LocalDateTime limite = agora.minusHours(24);
        transactionTemplate.executeWithoutResult(tx -> {
            Set<Long> agendamentosAfetados = new LinkedHashSet<>();
            for (AgendamentoEntrega e : pendentes) {
                List<String> ids = parseIds(e.getReceiptsPendentes());
                boolean algumEntregue = ids.stream().anyMatch(id -> Boolean.TRUE.equals(status.get(id)));
                boolean todosResolvidos = ids.stream().allMatch(status::containsKey);
                EstadoEntrega novoEstado = null;
                if (algumEntregue) {
                    novoEstado = EstadoEntrega.NOTIFICACAO_ENTREGUE;
                } else if (todosResolvidos) { // nenhum ok e nenhum pendente → todos com erro
                    novoEstado = EstadoEntrega.SEM_NOTIFICACAO_ATIVA;
                } else if (!e.getCriadoEm().isBefore(limite)) {
                    continue; // ainda pendente e dentro das 24h: tenta no próximo ciclo
                }
                // Para de reconsultar esta linha: zera SÓ o marcador técnico (o status é preservado).
                Long agendamentoId = e.getAgendamento().getId();
                e.setReceiptsPendentes(null);
                entregaRepository.save(e); // merge do 'e' destacado — só limpa receipts_pendentes
                if (novoEstado != null) {
                    // Append-only: cada mudança é um NOVO registro; nada é sobrescrito (histórico completo).
                    entregaRepository.save(novoEvento(e, novoEstado, agendamentoId, agora));
                    agendamentosAfetados.add(agendamentoId);
                }
            }
            agendamentosAfetados.forEach(this::recomputarResumo);
        });
    }

    /** Novo evento de entrega copiando a identidade do destinatário (append-only). */
    private AgendamentoEntrega novoEvento(AgendamentoEntrega origem, EstadoEntrega estado, Long agendamentoId,
            LocalDateTime agora) {
        AgendamentoEntrega novo = new AgendamentoEntrega();
        novo.setAgendamento(agendamentoRepository.getReferenceById(agendamentoId)); // ref gerenciada p/ o FK
        novo.setTipo(origem.getTipo());
        novo.setResponsavelId(origem.getResponsavelId());
        novo.setNome(origem.getNome());
        novo.setTelefone(origem.getTelefone());
        novo.setEstado(estado);
        novo.setCriadoEm(agora);
        return novo;
    }

    /**
     * Recalcula o resumo do agendamento a partir do estado ATUAL de cada pessoa (a última linha
     * do grupo tipo+responsável — a tabela é append-only), consolidado por {@link #combinar}.
     */
    private void recomputarResumo(Long agendamentoId) {
        Map<String, EstadoEntrega> ultimoPorPessoa = new LinkedHashMap<>();
        for (AgendamentoEntrega linha : entregaRepository.findByAgendamento_IdOrderByIdAsc(agendamentoId)) {
            ultimoPorPessoa.put(chaveDaPessoa(linha), linha.getEstado()); // asc → o último put é o mais recente
        }
        EstadoEntrega resumo = null;
        for (EstadoEntrega estado : ultimoPorPessoa.values()) {
            resumo = combinar(resumo, estado);
        }
        EstadoEntrega resumoFinal = resumo;
        agendamentoRepository.findById(agendamentoId).ifPresent(ag -> ag.setEntregaResumo(resumoFinal));
    }

    /**
     * Chave estável do destinatário para agrupar os eventos. Responsável usa o id; se ele foi
     * EXCLUÍDO do cadastro (FK responsavel_id ON DELETE SET NULL), cai no telefone/nome congelados —
     * senão dois responsáveis excluídos colapsariam na mesma chave (id nulo) e se misturariam.
     */
    private static String chaveDaPessoa(AgendamentoEntrega e) {
        if (e.getTipo() == TipoDestinatario.PACIENTE) {
            return "PACIENTE";
        }
        return e.getResponsavelId() != null
                ? "RESP:" + e.getResponsavelId()
                : "RESP:" + e.getTelefone() + "|" + e.getNome();
    }

    /** Receipt ids "ok" dos aparelhos da pessoa, juntos por vírgula (nulo se nenhum). */
    private static String receiptsDe(Destinatario d, Map<String, String> receiptIdPorToken) {
        String ids = d.tokens().stream()
                .map(receiptIdPorToken::get)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.joining(","));
        return ids.isBlank() ? null : ids;
    }

    private static List<String> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    /** Paciente + responsáveis ATIVOS com acesso a Agendamentos (mesma regra do fan-out do push). */
    private List<Destinatario> resolverDestinatarios(Paciente paciente) {
        List<Destinatario> destinatarios = new ArrayList<>();
        destinatarios.add(new Destinatario(TipoDestinatario.PACIENTE, null, paciente.getNome(),
                paciente.getTelefone(), tokensDe(paciente.getTelefone(), paciente.getId())));
        for (Responsavel r : responsavelRepository.findByPaciente_Id(paciente.getId())) {
            if (!r.isAtivo()) {
                continue;
            }
            boolean acessa = r.getPermissoes().getOrDefault(FuncionalidadeApp.AGENDAMENTOS,
                    NivelAcessoResponsavel.SEM_ACESSO) != NivelAcessoResponsavel.SEM_ACESSO;
            if (!acessa) {
                continue;
            }
            destinatarios.add(new Destinatario(TipoDestinatario.RESPONSAVEL, r.getId(), r.getNome(),
                    r.getTelefone(), tokensDe(r.getTelefone(), null)));
        }
        return destinatarios;
    }

    /** Tokens de push da pessoa: pela conta (telefone) + legados por paciente (só para o próprio paciente). */
    private List<String> tokensDe(String telefone, Long pacienteIdLegado) {
        List<Dispositivo> aparelhos = new ArrayList<>();
        String tel = Documentos.somenteDigitos(telefone);
        if (tel != null && !tel.isBlank()) {
            contaRepository.findByTelefone(tel)
                    .ifPresent(conta -> aparelhos.addAll(dispositivoRepository.findByContaId(conta.getId())));
        }
        if (pacienteIdLegado != null) {
            aparelhos.addAll(dispositivoRepository.findByPacienteIdAndContaIdIsNull(pacienteIdLegado));
        }
        return aparelhos.stream().map(Dispositivo::getToken).distinct().toList();
    }

    private static String comPrefixo(String nome, String titulo) {
        String primeiro = nome == null ? "" : nome.trim().split("\\s+")[0];
        return primeiro.isEmpty() ? titulo : primeiro + ": " + titulo;
    }
}
