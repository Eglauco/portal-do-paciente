package com.example.pop.agendamento;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.sms.SmsService;

/**
 * Convite MANUAL do app, feito pelo atendente nas telas de agenda/horário: abrir o WhatsApp com a mensagem
 * pronta e enviar o SMS de convite. Usa o mesmo texto do convite automático
 * ({@link ConviteAppService#textoConvite()}) e só os celulares do cadastro do PACIENTE.
 *
 * <p>Regras (decididas com o dono): os botões só valem para quem NÃO tem o app; o SMS funciona mesmo com o
 * convite automático desligado (só exige o texto configurado); o número tem que ser um celular do cadastro
 * do paciente (nunca um número livre); cada uso vai para o histórico do horário com o número MASCARADO.
 */
@Service
public class HorarioConviteService {

    private static final Logger log = LoggerFactory.getLogger(HorarioConviteService.class);
    /** Teto de pacientes por consulta de opções (uma agenda/página nunca chega perto disso). */
    static final int MAX_PACIENTES = 300;

    private final HorarioRepository horarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ConviteAppService conviteAppService;
    private final HorarioLogService logService;
    private final SmsService smsService;
    private final TransactionTemplate leitura;

    public HorarioConviteService(HorarioRepository horarioRepository, PacienteRepository pacienteRepository,
            ConviteAppService conviteAppService, HorarioLogService logService, SmsService smsService,
            PlatformTransactionManager transactionManager) {
        this.horarioRepository = horarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.conviteAppService = conviteAppService;
        this.logService = logService;
        this.smsService = smsService;
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    /** Um celular do paciente: E.164 (vai para o link/envio) e a forma de exibição. */
    public record Telefone(String numero, String exibicao) {
        static Telefone de(String e164) {
            return new Telefone(e164, ConviteAppService.exibir(e164));
        }
    }

    /** Situação de um paciente para o convite: já tem o app? e os celulares do cadastro. */
    public record PacienteConvite(boolean temApp, List<Telefone> telefones) {
    }

    /** Opções do convite para uma lista de pacientes + o texto (uma vez só; {@code null} se não configurado). */
    public record Opcoes(String texto, Map<Long, PacienteConvite> pacientes) {
    }

    /** Resultado do envio por SMS: números (formatados) aceitos pelo provedor e os que falharam. */
    public record ResultadoSms(List<String> enviados, List<String> falhas) {
    }

    /** Opções do convite (tem app? celulares) de cada paciente pedido + o texto configurado. */
    public Opcoes opcoes(Collection<Long> pacienteIds) {
        List<Long> ids = pacienteIds == null ? List.of()
                : pacienteIds.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_PACIENTES).toList();
        Map<Long, PacienteConvite> mapa = leitura.execute(status -> {
            Map<Long, PacienteConvite> m = new LinkedHashMap<>();
            if (!ids.isEmpty()) {
                for (Paciente p : pacienteRepository.findAllById(ids)) {
                    m.put(p.getId(), situacao(p));
                }
            }
            return m;
        });
        return new Opcoes(conviteAppService.textoConvite(), mapa == null ? Map.of() : mapa);
    }

    /**
     * Envia o SMS de convite para os celulares escolhidos (todos têm que ser do cadastro do paciente do horário).
     * Os POSTs ao provedor ficam FORA de transação; cada envio aceito vai para o histórico do horário.
     */
    public ResultadoSms enviarSms(Long horarioId, List<String> telefones, Long usuarioId) {
        String texto = conviteAppService.textoConvite();
        if (texto == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O texto do convite não está configurado. Preencha em Configurações antes de enviar.");
        }
        if (!smsService.configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nenhum provedor de SMS está configurado no servidor.");
        }
        Contexto ctx = contexto(horarioId);
        if (ctx.temApp()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este paciente já tem o app instalado — o convite não é necessário.");
        }
        List<String> destinos = validarNumeros(telefones, ctx.celulares());

        List<String> enviados = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (String e164 : destinos) {
            if (smsService.enviar(e164, texto)) {
                enviados.add(ConviteAppService.exibir(e164));
                registrarSemQuebrar(ctx.horario(), EventoLogHorario.CONVITE_SMS, e164, usuarioId);
            } else {
                falhas.add(ConviteAppService.exibir(e164));
            }
        }
        log.info("Convite manual por SMS (horário {}): {} de {} número(s) enviados.",
                horarioId, enviados.size(), destinos.size());
        return new ResultadoSms(enviados, falhas);
    }

    /** Registra no histórico que o atendente abriu o WhatsApp com o convite para este celular do paciente. */
    public void registrarWhatsapp(Long horarioId, String telefone, Long usuarioId) {
        Contexto ctx = contexto(horarioId);
        String e164 = validarNumeros(telefone == null ? List.of() : List.of(telefone), ctx.celulares()).get(0);
        logService.registrarEvento(ctx.horario(), EventoLogHorario.CONVITE_WHATSAPP,
                ConviteAppService.mascarar(e164), usuarioId);
    }

    /**
     * Grava o evento no histórico sem derrubar o envio: o SMS JÁ saiu (e foi cobrado) — se a auditoria falhar
     * (ex.: horário excluído ao mesmo tempo), o atendente não pode ver "erro" e reenviar em duplicidade.
     */
    private void registrarSemQuebrar(Horario horario, EventoLogHorario evento, String e164, Long usuarioId) {
        try {
            logService.registrarEvento(horario, evento, ConviteAppService.mascarar(e164), usuarioId);
        } catch (RuntimeException e) {
            log.warn("Convite enviado, mas não foi possível registrar no histórico do horário {}: {}",
                    horario.getId(), e.getMessage());
        }
    }

    /** Horário + o que o convite precisa do paciente, lido numa transação curta. 404 se o horário não existe. */
    private Contexto contexto(Long horarioId) {
        Contexto ctx = leitura.execute(status -> horarioRepository.findById(horarioId)
                .map(h -> new Contexto(h, conviteAppService.pacienteTemApp(h.getPaciente()),
                        ConviteAppService.celularesDoPaciente(h.getPaciente())))
                .orElse(null));
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário não encontrado.");
        }
        return ctx;
    }

    private PacienteConvite situacao(Paciente p) {
        return new PacienteConvite(conviteAppService.pacienteTemApp(p),
                ConviteAppService.celularesDoPaciente(p).stream().map(Telefone::de).toList());
    }

    /**
     * Normaliza os números pedidos para E.164 e exige que TODOS sejam celulares do cadastro do paciente
     * (o atendente escolhe numa lista — nunca digita um número livre). Package-private para teste.
     */
    static List<String> validarNumeros(List<String> pedidos, List<String> celulares) {
        Set<String> escolhidos = new LinkedHashSet<>();
        if (pedidos != null) {
            for (String p : pedidos) {
                if (p != null && !p.isBlank()) {
                    escolhidos.add(ConviteAppService.e164(p));
                }
            }
        }
        if (escolhidos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha ao menos um número.");
        }
        if (!celulares.containsAll(escolhidos)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O número escolhido não é um celular do cadastro deste paciente.");
        }
        return new ArrayList<>(escolhidos);
    }

    private record Contexto(Horario horario, boolean temApp, List<String> celulares) {
    }
}
