package com.example.pop.agendamento;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.paciente.ContaApp;
import com.example.pop.paciente.ContaAppRepository;
import com.example.pop.paciente.Documentos;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.push.DispositivoRepository;
import com.example.pop.sms.SmsService;

/**
 * Convite por SMS para baixar o app, disparado quando um Horário é criado (manual ou importado
 * do SIRESP — ambos passam por {@link HorarioEntregaService#notificarNovoAgendamento}). Envia só
 * para quem (paciente ou responsável) <b>não tem o aplicativo</b>, em TODOS os celulares
 * cadastrados, a cada agendamento.
 *
 * <p>"Sem app" = nenhum dispositivo (sessão) vinculado — pela conta do CPF e, no caso do paciente,
 * também pelos dispositivos legados por id (mesma noção do fan-out do push). Os dois links de loja,
 * o liga/desliga e o nome da plataforma vêm das {@link ConfiguracaoService configurações} globais.
 *
 * <p>Roda <b>assíncrono</b> e <b>fail-open</b>: a leitura dos destinatários entra numa transação
 * curta (read-only) e os POSTs ao Twilio ficam FORA de transação; qualquer falha é só logada e
 * NUNCA afeta o agendamento nem o push.
 */
@Service
public class ConviteAppService {

    private static final Logger log = LoggerFactory.getLogger(ConviteAppService.class);

    private final ConfiguracaoService configuracaoService;
    private final PacienteRepository pacienteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final ContaAppRepository contaRepository;
    private final DispositivoRepository dispositivoRepository;
    private final SmsService smsService;
    private final TransactionTemplate transactionTemplate;

    public ConviteAppService(ConfiguracaoService configuracaoService, PacienteRepository pacienteRepository,
            ResponsavelRepository responsavelRepository, ContaAppRepository contaRepository,
            DispositivoRepository dispositivoRepository, SmsService smsService,
            PlatformTransactionManager transactionManager) {
        this.configuracaoService = configuracaoService;
        this.pacienteRepository = pacienteRepository;
        this.responsavelRepository = responsavelRepository;
        this.contaRepository = contaRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.smsService = smsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    /**
     * Dispara (async) o convite para quem está ligado a este paciente e não tem o app. Fail-open:
     * tudo é capturado e logado — o agendamento/push nunca quebra por aqui.
     */
    @Async("smsConviteExecutor")
    public void convidarSeSemApp(Long pacienteId) {
        try {
            if (pacienteId == null || !configuracaoService.lerBooleano(ChaveConfiguracao.SMS_CONVITE_APP_HABILITADO)) {
                return;
            }
            String corpo = montarMensagem();
            if (corpo == null) {
                log.debug("Convite por SMS ligado, mas sem link de loja configurado — nada enviado.");
                return;
            }
            if (!smsService.configurado()) {
                log.warn("Convite por SMS ligado, mas o Twilio Messaging não está configurado — nada enviado.");
                return;
            }
            List<String> telefones = carregarTelefonesSemApp(pacienteId);
            if (telefones.isEmpty()) {
                return;
            }
            // POSTs ao Twilio FORA de transação (a leitura dos destinatários já foi feita e fechada).
            int enviados = 0;
            for (String e164 : telefones) {
                if (smsService.enviar(e164, corpo)) {
                    enviados++;
                }
            }
            log.info("Convite por SMS (paciente {}): {} de {} número(s) enviados.",
                    pacienteId, enviados, telefones.size());
        } catch (RuntimeException e) {
            // Fail-open total: o convite é acessório; nunca derruba o agendamento nem o push.
            log.warn("Falha ao enviar convite por SMS (paciente {}): {}", pacienteId, e.getMessage());
        }
    }

    /** Corpo do SMS com os links disponíveis; {@code null} se nenhum link estiver configurado. */
    private String montarMensagem() {
        String android = vazioParaNulo(configuracaoService.lerTexto(ChaveConfiguracao.SMS_CONVITE_APP_LINK_ANDROID));
        String ios = vazioParaNulo(configuracaoService.lerTexto(ChaveConfiguracao.SMS_CONVITE_APP_LINK_IOS));
        if (android == null && ios == null) {
            return null;
        }
        String marca = vazioParaNulo(configuracaoService.lerTexto(ChaveConfiguracao.NOME_PLATAFORMA));
        String nomeApp = marca == null ? "nosso aplicativo" : marca;
        StringBuilder sb = new StringBuilder("Olá! Você tem um atendimento agendado. Baixe o ")
                .append(nomeApp).append(" para acompanhar e confirmar.");
        if (android != null) {
            sb.append(" Android: ").append(android);
        }
        if (ios != null) {
            sb.append(" iOS: ").append(ios);
        }
        return sb.toString();
    }

    /** Telefones (E.164, só celular, sem repetição) das pessoas SEM app ligadas a este paciente. */
    private List<String> carregarTelefonesSemApp(Long pacienteId) {
        List<String> numeros = transactionTemplate.execute(status -> {
            Set<String> fones = new LinkedHashSet<>();
            Paciente paciente = pacienteRepository.findById(pacienteId).orElse(null);
            if (paciente != null) {
                if (!temApp(paciente.getCpf(), paciente.getId())) {
                    for (String telefone : paciente.getTelefonesAdicionais()) {
                        adicionarSeCelular(fones, telefone);
                    }
                }
                for (Responsavel r : responsavelRepository.findByPaciente_Id(pacienteId)) {
                    if (!r.isAtivo() || temApp(r.getCpf(), null)) {
                        continue;
                    }
                    adicionarSeCelular(fones, r.getTelefone());
                }
            }
            return new ArrayList<>(fones);
        });
        return numeros == null ? List.of() : numeros;
    }

    /** "Tem app" = algum dispositivo (sessão) vinculado: pela conta do CPF, ou legado por id (só paciente). */
    private boolean temApp(String cpf, Long pacienteIdLegado) {
        String c = Documentos.somenteDigitos(cpf);
        if (c != null) {
            ContaApp conta = contaRepository.findByCpf(c).orElse(null);
            if (conta != null && !dispositivoRepository.findByContaId(conta.getId()).isEmpty()) {
                return true;
            }
        }
        return pacienteIdLegado != null
                && !dispositivoRepository.findByPacienteIdAndContaIdIsNull(pacienteIdLegado).isEmpty();
    }

    /** Adiciona o número (em E.164) se parecer um celular brasileiro; descarta fixo (o Twilio rejeita). */
    private static void adicionarSeCelular(Set<String> destino, String telefone) {
        if (pareceCelular(telefone)) {
            destino.add(e164(telefone));
        }
    }

    /** Celular BR: parte nacional com 11 dígitos (DDD + 9) e o 1º dígito do assinante = 9. */
    static boolean pareceCelular(String telefone) {
        String d = Documentos.somenteDigitos(telefone);
        if (d == null) {
            return false;
        }
        if (d.startsWith("55") && d.length() >= 12) {
            d = d.substring(2);
        }
        return d.length() == 11 && d.charAt(2) == '9';
    }

    /** Monta o E.164 assumindo Brasil (+55) quando o número não traz o código do país. */
    static String e164(String telefone) {
        String d = Documentos.somenteDigitos(telefone);
        if (d == null) {
            return null;
        }
        return (d.startsWith("55") && d.length() >= 12) ? "+" + d : "+55" + d;
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
