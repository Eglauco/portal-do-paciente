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

import com.example.pop.agendamento.Horario;
import com.example.pop.chat.Chat;
import com.example.pop.inquilino.InquilinoService;
import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.nps.Nps;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteAcessoService.DestinoPush;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.postagem.Postagem;
import com.example.pop.tenant.TenantContext;

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
    private static final String EXPO_RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";
    private static final int LOTE = 100;
    private static final int LOTE_RECEIPTS = 1000; // limite da Expo por consulta de getReceipts
    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final DispositivoRepository repository;
    private final NotificacaoService notificacaoService;
    private final PacienteAcessoService acessoService;
    private final PacienteRepository pacienteRepository;
    private final InquilinoService inquilinoService;
    private final PushAckToken pushAckToken;
    private RestClient restClient;

    public PushService(DispositivoRepository repository, NotificacaoService notificacaoService,
            PacienteAcessoService acessoService, PacienteRepository pacienteRepository,
            InquilinoService inquilinoService, PushAckToken pushAckToken) {
        this.repository = repository;
        this.notificacaoService = notificacaoService;
        this.acessoService = acessoService;
        this.pacienteRepository = pacienteRepository;
        this.inquilinoService = inquilinoService;
        this.pushAckToken = pushAckToken;
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

    /** Falta registrada — pede ao paciente para justificar a ausência. */
    public void notificarFaltaPaciente(Horario a) {
        String corpo = "Você foi marcado como falta na consulta de " + a.getEspecialidade().getNome()
                + " em " + a.getDataHora().format(DATA_FMT) + ". Toque para justificar.";
        Map<String, Object> data = Map.of("tipo", "FALTA", "agendamentoId", a.getId());
        notificacaoService.registrar(a.getPaciente().getId(), TipoNotificacao.FALTA, "Falta registrada", corpo, a.getId());
        notificarPaciente(a.getPaciente().getId(), FuncionalidadeApp.AGENDAMENTOS, "Falta registrada", corpo, data);
    }

    /**
     * Nova mensagem da unidade no chat — só o paciente da conversa. Leva um TOKEN DE ENTREGA
     * assinado ({@code data.ack}) e {@code mutableContent} para confirmar o 2º "check" com o app
     * FECHADO: no iOS a Notification Service Extension dispara (mesmo force-quit) e no Android a
     * background task acorda — ambos ecoam o token para {@code POST /push/entregue} sem Bearer.
     */
    public void notificarNovaMensagem(Chat chat) {
        String corpo = "Você recebeu uma nova mensagem de " + chat.getUnidadeSaude().getNome() + ".";
        Long pacienteId = chat.getPaciente().getId();
        Map<String, Object> data = new HashMap<>();
        data.put("tipo", "CHAT");
        data.put("chatId", chat.getId());
        // O inquilino atual (schema da thread do envio) vai DENTRO do token assinado, não cru no data.
        Long inquilinoId = inquilinoService.idPorSchema(TenantContext.atual());
        data.put("ack", pushAckToken.gerar(inquilinoId, pacienteId));
        // mutableContent:true → aps.mutable-content:1 (iOS roda a NSE, inclusive com app encerrado).
        // priority:high (já padrão em enviarLote) → entrega imediata e acorda a task no Android.
        notificarPaciente(pacienteId, FuncionalidadeApp.CHAT, "Nova mensagem", corpo, data,
                Map.of("mutableContent", true));
        // Android: além da notificação visível acima, um push SILENCIOSO (data-only) que acorda a
        // task em background para confirmar a entrega (2º "check") com o app FECHADO. É separado e
        // best-effort: se não disparar (force-stop/Doze), a notificação visível NÃO regride e o 2º
        // check ainda sai ao reabrir (rede de segurança POST /meu/chats/entregar-pendentes).
        enviarAckEntregaAndroid(pacienteId, data);
    }

    /** Nova avaliação NPS pendente — só o paciente do atendimento. */
    public void notificarNpsPendente(Nps nps) {
        String especialidade = nps.getHorario().getEspecialidade().getNome();
        String corpo = "Como foi seu atendimento de " + especialidade + "? Toque para avaliar.";
        Long pacienteId = nps.getHorario().getPaciente().getId();
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
        notificarPaciente(pacienteId, funcionalidade, titulo, corpo, data, Map.of());
    }

    /**
     * Igual ao {@link #notificarPaciente(Long, FuncionalidadeApp, String, String, Map)}, mas com
     * {@code extrasTopo} — campos adicionais no NÍVEL da mensagem Expo (ex.: {@code mutableContent}
     * para o chat). Só o chat usa; os demais push chamam a versão sem extras.
     */
    public void notificarPaciente(Long pacienteId, FuncionalidadeApp funcionalidade, String titulo, String corpo,
            Map<String, Object> data, Map<String, Object> extrasTopo) {
        if (pacienteId == null) {
            return;
        }
        // Fan-out respeitando a permissão: responsável sem acesso àquela funcionalidade não recebe.
        DestinoPush destino = acessoService.destinoPush(pacienteId, funcionalidade);
        List<String> tokens = aparelhosDe(destino, pacienteId).stream()
                .map(Dispositivo::getToken).distinct().toList();
        enviar(tokens, comPrefixo(destino.nome(), titulo), corpo, comPacienteId(data, pacienteId), extrasTopo);
    }

    /**
     * Aparelhos que devem receber o push de um paciente: todas as contas que o acessam (fan-out por
     * conta) + aparelhos legados sem conta. Reaproveitado pelo push visível e pelo ACK silencioso.
     */
    private java.util.Collection<Dispositivo> aparelhosDe(DestinoPush destino, Long pacienteId) {
        Map<Long, Dispositivo> aparelhos = new LinkedHashMap<>();
        if (!destino.contaIds().isEmpty()) {
            repository.findByContaIdIn(destino.contaIds()).forEach(d -> aparelhos.put(d.getId(), d));
        }
        // Só conta_id nulo — um aparelho já migrado é resolvido pela conta (autorização atual),
        // evitando vazar o push para uma conta que perdeu o acesso ao paciente.
        repository.findByPacienteIdAndContaIdIsNull(pacienteId).forEach(d -> aparelhos.put(d.getId(), d));
        return aparelhos.values();
    }

    /**
     * Push SILENCIOSO (data-only) adicional SÓ para aparelhos Android: acorda a task em background
     * para ela confirmar a entrega (POST /push/entregue) com o app FECHADO — o recibo "entregue"
     * estilo WhatsApp. Não exibe nada (a notificação visível vai à parte). O iOS usa a NSE.
     */
    private void enviarAckEntregaAndroid(Long pacienteId, Map<String, Object> data) {
        DestinoPush destino = acessoService.destinoPush(pacienteId, FuncionalidadeApp.CHAT);
        List<String> tokens = aparelhosDe(destino, pacienteId).stream()
                .filter(d -> "android".equalsIgnoreCase(d.getPlataforma()))
                .map(Dispositivo::getToken).distinct().toList();
        enviarSilencioso(tokens, comPacienteId(data, pacienteId));
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
        enviar(tokens, titulo, corpo, data, Map.of());
    }

    private void enviar(List<String> tokens, String titulo, String corpo, Map<String, Object> data,
            Map<String, Object> extrasTopo) {
        if (tokens.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            enviarLote(lote, titulo, corpo, data, extrasTopo);
        }
    }

    private void enviarLote(List<String> tokens, String titulo, String corpo, Map<String, Object> data,
            Map<String, Object> extrasTopo) {
        List<Map<String, Object>> mensagens = new ArrayList<>();
        for (String token : tokens) {
            Map<String, Object> mensagem = new LinkedHashMap<>();
            mensagem.put("to", token);
            mensagem.put("title", titulo);
            mensagem.put("body", corpo);
            mensagem.put("data", data);
            mensagem.put("channelId", "default");
            mensagem.put("priority", "high");
            mensagem.put("sound", "default");
            mensagem.putAll(extrasTopo); // ex.: mutableContent para o chat (NSE do iOS)
            mensagens.add(mensagem);
        }
        postExpo(mensagens);
    }

    /** POST em lote na Expo Push API; falhas são engolidas (o push nunca quebra o fluxo de origem). */
    private void postExpo(List<Map<String, Object>> mensagens) {
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

    /** Envia um push SILENCIOSO (data-only, alta prioridade) em lotes — só acorda a task, não exibe. */
    private void enviarSilencioso(List<String> tokens, Map<String, Object> data) {
        if (tokens.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            List<Map<String, Object>> mensagens = new ArrayList<>();
            for (String token : lote) {
                Map<String, Object> mensagem = new LinkedHashMap<>();
                mensagem.put("to", token);
                mensagem.put("data", data);
                // SEM title/body/channelId/sound: data-only para a task em background rodar com o app
                // fechado (um "notification message" não acordaria a task no Android).
                mensagem.put("_contentAvailable", true);
                mensagem.put("priority", "high");
                mensagem.put("ttl", 150); // curto: o recibo de entrega só faz sentido agora.
                mensagens.add(mensagem);
            }
            postExpo(mensagens);
        }
    }

    /**
     * Resultado do envio de UM token: se a Expo ACEITOU a mensagem (ticket "ok") e o
     * {@code receiptId} do ticket (para consultar a entrega depois via getReceipts). receiptId
     * é nulo quando não foi aceito.
     */
    public record ResultadoEnvio(String token, boolean aceito, String receiptId) {
    }

    /**
     * Envia e DEVOLVE o resultado por token (lê os tickets da Expo, que o envio comum
     * descarta) — base do rastreio de entrega do agendamento. Uma falha de rede/Expo marca
     * todos os tokens do lote como não aceitos (não sabemos se chegaram).
     */
    public List<ResultadoEnvio> enviarComResultado(String titulo, String corpo, Map<String, Object> data,
            List<String> tokens) {
        List<ResultadoEnvio> resultados = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            resultados.addAll(enviarLoteComResultado(lote, titulo, corpo, data));
        }
        return resultados;
    }

    @SuppressWarnings("unchecked")
    private List<ResultadoEnvio> enviarLoteComResultado(List<String> tokens, String titulo, String corpo,
            Map<String, Object> data) {
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
            Map<String, Object> resposta = client().post()
                    .uri(EXPO_PUSH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(mensagens)
                    .retrieve()
                    .body(Map.class);
            // A Expo devolve { "data": [ {status:"ok"|"error", ...}, ... ] } alinhado por índice.
            List<Map<String, Object>> tickets = resposta == null ? null
                    : (List<Map<String, Object>>) resposta.get("data");
            List<ResultadoEnvio> out = new ArrayList<>();
            int aceitos = 0;
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                Map<String, Object> ticket = (tickets != null && i < tickets.size()) ? tickets.get(i) : null;
                boolean aceito = ticket != null && "ok".equals(String.valueOf(ticket.get("status")));
                String receiptId = aceito && ticket.get("id") != null ? String.valueOf(ticket.get("id")) : null;
                if (aceito) {
                    aceitos++;
                } else {
                    String erro = ticket == null ? "sem ticket da Expo" : erroDoTicket(ticket);
                    log.warn("Push NÃO aceito pela Expo (token {}): {}", resumoToken(token), erro);
                    if ("DeviceNotRegistered".equals(erro)) {
                        removerTokenMorto(token); // token/instalação inválida: limpa a base
                    }
                }
                out.add(new ResultadoEnvio(token, aceito, receiptId));
            }
            log.info("Push enviado à Expo: {} token(s), {} aceito(s), {} recusado(s)",
                    tokens.size(), aceitos, tokens.size() - aceitos);
            return out;
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar notificação push (com resultado): {}", e.getMessage());
            return tokens.stream().map(t -> new ResultadoEnvio(t, false, null)).toList();
        }
    }

    /**
     * Consulta os RECEIPTS (getReceipts) da Expo para confirmar a ENTREGA ao aparelho.
     * Devolve: id presente = true (entregue/ok) ou false (erro, ex.: DeviceNotRegistered);
     * id AUSENTE do mapa = ainda não pronto (tentar de novo depois). Falha de rede não
     * resolve nada (ids ficam ausentes).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> consultarReceipts(List<String> receiptIds) {
        Map<String, Boolean> resultado = new HashMap<>();
        for (int i = 0; i < receiptIds.size(); i += LOTE_RECEIPTS) {
            List<String> lote = receiptIds.subList(i, Math.min(i + LOTE_RECEIPTS, receiptIds.size()));
            try {
                Map<String, Object> resposta = client().post()
                        .uri(EXPO_RECEIPTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(Map.of("ids", lote))
                        .retrieve()
                        .body(Map.class);
                Object data = resposta == null ? null : resposta.get("data");
                if (data instanceof Map<?, ?> mapa) {
                    mapa.forEach((id, valor) -> {
                        if (valor instanceof Map<?, ?> receipt) {
                            boolean ok = "ok".equals(String.valueOf(receipt.get("status")));
                            resultado.put(String.valueOf(id), ok);
                            if (!ok) {
                                log.warn("Receipt de push com erro (id {}): {}", id, erroDoTicket((Map<String, Object>) receipt));
                            }
                        }
                    });
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao consultar receipts push: {}", e.getMessage());
            }
        }
        return resultado;
    }

    /** Erro legível de um ticket/receipt da Expo: details.error (ex.: DeviceNotRegistered) ou message. */
    private static String erroDoTicket(Map<String, Object> ticket) {
        Object details = ticket.get("details");
        if (details instanceof Map<?, ?> d && d.get("error") != null) {
            return String.valueOf(d.get("error"));
        }
        Object msg = ticket.get("message");
        return msg != null ? String.valueOf(msg) : "erro desconhecido";
    }

    /** Prefixo do token para log (não expõe o token inteiro). */
    private static String resumoToken(String token) {
        if (token == null) {
            return "null";
        }
        return token.length() <= 24 ? token : token.substring(0, 24) + "…";
    }

    /** Remove um Expo Push Token inválido (DeviceNotRegistered) — mantém a base de dispositivos limpa. */
    private void removerTokenMorto(String token) {
        try {
            repository.findFirstByToken(token).ifPresent(d -> {
                repository.delete(d);
                log.info("Token de push removido (DeviceNotRegistered): dispositivo id {}", d.getId());
            });
        } catch (RuntimeException e) {
            log.warn("Falha ao remover token morto: {}", e.getMessage());
        }
    }
}
