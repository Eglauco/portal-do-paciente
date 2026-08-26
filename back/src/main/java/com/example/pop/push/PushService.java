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

    /** Notifica todos os dispositivos sobre um novo agendamento aguardando confirmação. */
    public void notificarNovoAgendamento(Agendamento a) {
        String corpo = "Consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT)
                + ". Toque para confirmar ou cancelar.";
        Map<String, Object> data = Map.of("tipo", "AGENDAMENTO", "agendamentoId", a.getId());
        notificarTodos("Novo agendamento", corpo, data);
    }

    /** Envia uma notificação para todos os dispositivos registrados. */
    public void notificarTodos(String titulo, String corpo, Map<String, Object> data) {
        List<String> tokens = repository.findAll().stream().map(Dispositivo::getToken).toList();
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
