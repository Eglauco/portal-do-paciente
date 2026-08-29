package com.example.pop.push;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.chat.Chat;
import com.example.pop.nps.Nps;
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
    private RestClient restClient;

    public PushService(DispositivoRepository repository) {
        this.repository = repository;
    }

    // Criado sob demanda (evita abrir conexão na inicialização/testes).
    private synchronized RestClient client() {
        if (restClient == null) {
            restClient = RestClient.create();
        }
        return restClient;
    }

    /** Novo agendamento aguardando confirmação — só o paciente dono. */
    public void notificarNovoAgendamento(Agendamento a) {
        String corpo = "Consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT)
                + ". Toque para confirmar ou cancelar.";
        Map<String, Object> data = Map.of("tipo", "AGENDAMENTO", "agendamentoId", a.getId());
        notificarPaciente(a.getPaciente().getId(), "Novo agendamento", corpo, data);
    }

    /** Nova mensagem da unidade no chat — só o paciente da conversa. */
    public void notificarNovaMensagem(Chat chat) {
        String corpo = "Você recebeu uma nova mensagem de " + chat.getUnidadeSaude().getNome() + ".";
        Map<String, Object> data = Map.of("tipo", "CHAT", "chatId", chat.getId());
        notificarPaciente(chat.getPaciente().getId(), "Nova mensagem", corpo, data);
    }

    /** Nova avaliação NPS pendente — só o paciente do atendimento. */
    public void notificarNpsPendente(Nps nps) {
        String especialidade = nps.getAgendamento().getEspecialidade().getNome();
        String corpo = "Como foi seu atendimento de " + especialidade + "? Toque para avaliar.";
        notificarPaciente(nps.getAgendamento().getPaciente().getId(),
                "Avalie seu atendimento", corpo, Map.of("tipo", "NPS"));
    }

    /** Nova publicação (postagem) no feed das unidades — broadcast (todos os aparelhos). */
    public void notificarNovaPostagem(Postagem p) {
        String corpo = p.getUnidadeSaude().getNome() + " publicou: " + p.getTitulo();
        Map<String, Object> data = Map.of("tipo", "POSTAGEM", "postagemId", p.getId());
        notificarTodos("Nova publicação", corpo, data);
    }

    /** Novo prontuário (novo=true) ou novo documento (novo=false) — só o paciente dono. */
    public void notificarProntuario(Long pacienteId, boolean novo) {
        String titulo = novo ? "Novo prontuário" : "Novo documento";
        String corpo = novo
                ? "Seu atendimento foi registrado. Confira os documentos no prontuário."
                : "Um novo documento foi adicionado ao seu prontuário.";
        notificarPaciente(pacienteId, titulo, corpo, Map.of("tipo", "PRONTUARIO"));
    }

    /** Envia uma notificação só para os aparelhos de um paciente (push direcionado). */
    public void notificarPaciente(Long pacienteId, String titulo, String corpo, Map<String, Object> data) {
        if (pacienteId == null) {
            return;
        }
        enviar(repository.findByPacienteId(pacienteId).stream().map(Dispositivo::getToken).toList(),
                titulo, corpo, data);
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
