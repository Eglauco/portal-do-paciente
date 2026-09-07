package com.example.pop.push;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.chat.Chat;
import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.nps.Nps;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteAcessoService.DestinoPush;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.postagem.Postagem;

/**
 * Envia notificações push pela Expo Push API.
 * O app registra o Expo Push Token em /dispositivo; aqui buscamos todos os
 * tokens e disparamos a mensagem. Falhas de envio nunca quebram o fluxo que
 * originou a notificação.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final int LOTE = 100;
    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final DispositivoRepository repository;
    private final NotificacaoService notificacaoService;
    private final PacienteAcessoService acessoService;
    private final PacienteRepository pacienteRepository;
    private RestClient restClient;

    public PushService(DispositivoRepository repository, NotificacaoService notificacaoService,
            PacienteAcessoService acessoService, PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.notificacaoService = notificacaoService;
        this.acessoService = acessoService;
        this.pacienteRepository = pacienteRepository;
    }

    // Criado sob demanda (evita abrir conexão na inicialização/testes). Com timeouts:
    // uma API de push travada não pode segurar a thread do job nem a conexão de BD.
    private synchronized RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(10));
            restClient = RestClient.builder().requestFactory(factory).build();
        }
        return restClient;
    }

    /** Novo agendamento aguardando confirmação — só o paciente dono. */
    public void notificarNovoAgendamento(Agendamento a) {
        String corpo = "Consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT)
                + ". Toque para confirmar ou cancelar.";
        Map<String, Object> data = Map.of("tipo", "AGENDAMENTO", "agendamentoId", a.getId());
        notificacaoService.registrar(a.getPaciente().getId(), TipoNotificacao.AGENDAMENTO, "Novo agendamento", corpo, a.getId());
        notificarPaciente(a.getPaciente().getId(), FuncionalidadeApp.AGENDAMENTOS, "Novo agendamento", corpo, data);
    }

    /** Falta registrada — pede ao paciente para justificar a ausência. */
    public void notificarFaltaPaciente(Agendamento a) {
        String corpo = "Você foi marcado como falta na consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT) + ". Toque para justificar.";
        Map<String, Object> data = Map.of("tipo", "FALTA", "agendamentoId", a.getId());
        notificacaoService.registrar(a.getPaciente().getId(), TipoNotificacao.FALTA, "Falta registrada", corpo, a.getId());
        notificarPaciente(a.getPaciente().getId(), FuncionalidadeApp.AGENDAMENTOS, "Falta registrada", corpo, data);
    }

    /** Nova mensagem da unidade no chat — só o paciente da conversa. */
    public void notificarNovaMensagem(Chat chat) {
        String corpo = "Você recebeu uma nova mensagem de " + chat.getUnidadeSaude().getNome() + ".";
        Map<String, Object> data = Map.of("tipo", "CHAT", "chatId", chat.getId());
        notificarPaciente(chat.getPaciente().getId(), FuncionalidadeApp.CHAT, "Nova mensagem", corpo, data);
    }

    /** Nova avaliação NPS pendente — só o paciente do atendimento. */
    public void notificarNpsPendente(Nps nps) {
        String especialidade = nps.getAgendamento().getEspecialidade().getNome();
        String corpo = "Como foi seu atendimento de " + especialidade + "? Toque para avaliar.";
        Long pacienteId = nps.getAgendamento().getPaciente().getId();
        notificacaoService.registrar(pacienteId, TipoNotificacao.NPS, "Avalie seu atendimento", corpo, null);
        notificarPaciente(pacienteId, FuncionalidadeApp.NPS, "Avalie seu atendimento", corpo, Map.of("tipo", "NPS"));
    }

    /** Nova publicação (postagem) — só os pacientes vinculados à unidade da postagem (inbox + push). */
    public void notificarNovaPostagem(Postagem p) {
        String corpo = p.getUnidadeSaude().getNome() + " publicou: " + p.getTitulo();
        Map<String, Object> data = Map.of("tipo", "POSTAGEM", "postagemId", p.getId());
        // Só os pacientes vinculados à unidade da postagem recebem (inbox + push). O push
        // por paciente já respeita a permissão REDE_SOCIAL e ignora responsável sem acesso.
        for (Paciente paciente : pacienteRepository.findByUnidades_Id(p.getUnidadeSaude().getId())) {
            notificacaoService.registrar(paciente.getId(), TipoNotificacao.POSTAGEM, "Nova publicação", corpo, p.getId());
            notificarPaciente(paciente.getId(), FuncionalidadeApp.REDE_SOCIAL, "Nova publicação", corpo, data);
        }
    }

    /** Novo prontuário (novo=true) ou novo documento (novo=false) — só o paciente dono. */
    public void notificarProntuario(Long pacienteId, boolean novo) {
        String titulo = novo ? "Novo prontuário" : "Novo documento";
        String corpo = novo
                ? "Seu atendimento foi registrado. Confira os documentos no prontuário."
                : "Um novo documento foi adicionado ao seu prontuário.";
        notificacaoService.registrar(pacienteId, TipoNotificacao.PRONTUARIO, titulo, corpo, null);
        notificarPaciente(pacienteId, FuncionalidadeApp.PRONTUARIO, titulo, corpo, Map.of("tipo", "PRONTUARIO"));
    }

    /**
     * Push direcionado a um paciente: chega em TODAS as contas que o acessam (a
     * própria + responsáveis), mesmo que a conta esteja logada em outro perfil. O
     * título ganha o primeiro nome do perfil e o payload leva o pacienteId (o toque
     * troca de perfil no app).
     */
    public void notificarPaciente(Long pacienteId, FuncionalidadeApp funcionalidade, String titulo, String corpo,
            Map<String, Object> data) {
        if (pacienteId == null) {
            return;
        }
        // Fan-out respeitando a permissão: responsável sem acesso àquela funcionalidade não recebe.
        DestinoPush destino = acessoService.destinoPush(pacienteId, funcionalidade);
        Map<Long, Dispositivo> aparelhos = new LinkedHashMap<>();
        if (!destino.contaIds().isEmpty()) {
            repository.findByContaIdIn(destino.contaIds()).forEach(d -> aparelhos.put(d.getId(), d));
        }
        // Transição: aparelhos legados (sem conta) ainda registrados pelo paciente.
        // Só conta_id nulo — um aparelho já migrado é resolvido pela conta (autorização atual),
        // evitando vazar o push para uma conta que perdeu o acesso ao paciente.
        repository.findByPacienteIdAndContaIdIsNull(pacienteId).forEach(d -> aparelhos.put(d.getId(), d));

        List<String> tokens = aparelhos.values().stream().map(Dispositivo::getToken).distinct().toList();
        enviar(tokens, comPrefixo(destino.nome(), titulo), corpo, comPacienteId(data, pacienteId));
    }

    /** Prefixa o título com o primeiro nome do perfil ("Mariana: Novo agendamento"). */
    private static String comPrefixo(String nome, String titulo) {
        String primeiro = nome == null ? "" : nome.trim().split("\\s+")[0];
        return primeiro.isEmpty() ? titulo : primeiro + ": " + titulo;
    }

    /** Copia o data e adiciona o pacienteId alvo (para o toque trocar de perfil). */
    private static Map<String, Object> comPacienteId(Map<String, Object> data, Long pacienteId) {
        Map<String, Object> enriquecido = new HashMap<>(data);
        enriquecido.put("pacienteId", pacienteId);
        return enriquecido;
    }

    /** Envia uma notificação para todos os dispositivos registrados. */
    public void notificarTodos(String titulo, String corpo, Map<String, Object> data) {
        enviar(repository.findAll().stream().map(Dispositivo::getToken).toList(), titulo, corpo, data);
    }

    private void enviar(List<String> tokens, String titulo, String corpo, Map<String, Object> data) {
        if (tokens.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            enviarLote(lote, titulo, corpo, data);
        }
    }

    private void enviarLote(List<String> tokens, String titulo, String corpo, Map<String, Object> data) {
        List<Map<String, Object>> mensagens = new ArrayList<>();
        for (String token : tokens) {
            mensagens.add(Map.of(
                    "to", token,
                    "title", titulo,
                    "body", corpo,
                    "data", data,
                    "channelId", "default",
                    "priority", "high",
                    "sound", "default"));
        }
        try {
            client().post()
                    .uri(EXPO_PUSH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(mensagens)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar notificação push: {}", e.getMessage());
        }
    }
}
