package com.example.pop.siresp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agenda;
import com.example.pop.agendamento.AgendaRepository;
import com.example.pop.agendamento.Horario;
import com.example.pop.agendamento.HorarioLogService;
import com.example.pop.agendamento.HorarioRepository;
import com.example.pop.agendamento.HorarioEntregaService;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.agendamento.TipoAtendimento;
import com.example.pop.especialidade.Especialidade;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.exame.Exame;
import com.example.pop.exame.ExameRepository;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.Sexo;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.push.PushService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Regra de criação do AGENDAMENTO a partir de um registro do SIRESP. Dois passos:
 * <ol>
 *   <li>{@link #avaliar(Siresp)} — resolve, pelo CÓDIGO DE INTEGRAÇÃO, TODAS as informações necessárias
 *       (especialidade, unidade executante, profissional, paciente, data/hora e o procedimento padrão) e diz o
 *       que falta. Nada é criado se faltar algo (não gera "registro pela metade").</li>
 *   <li>{@link #processarRegistro(Long, Long)} — recalcula o diagnóstico e, se estiver tudo completo e ainda não
 *       houver horário para este registro, cria o horário (deduplicando por {@code ID_AGE_CONSULTA_HOR}),
 *       grava o log de auditoria e notifica o paciente. É o que o botão "Reprocessar" e a importação chamam: o
 *       usuário vai corrigindo os códigos e reprocessando até tudo ficar verde.</li>
 * </ol>
 * Reflete os DOIS níveis do CROSS: a <b>Agenda</b> (slot, {@code ID_AGE_CONSULTA}) é reaproveitada entre registros do
 * mesmo código — um ID_AGE_CONSULTA com vários pacientes vira UMA agenda com N horários; o <b>Horário</b> (marcação,
 * {@code ID_AGE_CONSULTA_HOR}) é criado por registro sob essa agenda.
 * Não é {@code @Transactional} de propósito (espelha o cadastro manual): cada save é uma tx curta e a notificação
 * (push) roda fora de transação.
 */
@Service
public class SirespAgendamentoService {

    private static final DateTimeFormatter DATA_HORA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Carimbo do "Processado em" (com segundos, para distinguir reprocessamentos próximos). */
    private static final DateTimeFormatter PROCESSADO_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    /** Hora dos campos de exibição do SIRESP (HH:mm:ss, igual ao HOR_INI/HOR_FIM do XML). */
    private static final DateTimeFormatter HORA_HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter[] DATAS = {
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy") };
    private static final DateTimeFormatter[] HORAS = {
            DateTimeFormatter.ofPattern("HH:mm:ss"), DateTimeFormatter.ofPattern("HH:mm") };

    private final SirespRepository repository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ExameRepository exameRepository;
    private final UnidadeRepository unidadeRepository;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final PacienteRepository pacienteRepository;
    private final HorarioRepository agendamentoRepository;
    private final AgendaRepository agendaRepository;
    private final HorarioLogService agendamentoLogService;
    private final HorarioEntregaService entregaService;
    private final PushService pushService;
    private final SirespConfigService configService;

    public SirespAgendamentoService(SirespRepository repository, EspecialidadeRepository especialidadeRepository,
            ExameRepository exameRepository, UnidadeRepository unidadeRepository,
            ProfissionalSaudeRepository profissionalRepository, PacienteRepository pacienteRepository,
            HorarioRepository agendamentoRepository, AgendaRepository agendaRepository,
            HorarioLogService agendamentoLogService,
            HorarioEntregaService entregaService, PushService pushService, SirespConfigService configService) {
        this.repository = repository;
        this.especialidadeRepository = especialidadeRepository;
        this.exameRepository = exameRepository;
        this.unidadeRepository = unidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.pacienteRepository = pacienteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.agendaRepository = agendaRepository;
        this.agendamentoLogService = agendamentoLogService;
        this.entregaService = entregaService;
        this.pushService = pushService;
        this.configService = configService;
    }

    /** Motivos de CANCELAMENTO do CROSS (ID_MOTIVO) → nome, para o log. */
    private static final Map<String, String> MOTIVO_CANCELAMENTO = Map.ofEntries(
            Map.entry("1", "Marcação errada"), Map.entry("2", "Paciente ligou desmarcando"),
            Map.entry("3", "Paciente faleceu"), Map.entry("4", "Transferência para outra data"),
            Map.entry("5", "Impossibilidade de confirmação com o paciente"), Map.entry("6", "Troca de paciente"),
            Map.entry("8", "Cancelado após recebimento de Torpedo"), Map.entry("9", "Cancelado pessoalmente"),
            Map.entry("10", "Cancelado via ligação realizada"), Map.entry("11", "Tratamento em outro serviço"));

    /** Motivos de TRANSFERÊNCIA do CROSS (ID_MOTIVO) → nome, para o log. */
    private static final Map<String, String> MOTIVO_TRANSFERENCIA = Map.ofEntries(
            Map.entry("1", "Licença do profissional"), Map.entry("2", "Férias do profissional"),
            Map.entry("3", "Equipamento quebrado"), Map.entry("4", "Solicitação do paciente"),
            Map.entry("5", "Desligamento do profissional"), Map.entry("6", "Alteração de agenda"),
            Map.entry("7", "Falta médica"));

    /** As peças necessárias ao agendamento + as linhas do diagnóstico. {@code completo()} = tudo resolvido. */
    public record Avaliacao(Especialidade especialidade, Unidade unidade, ProfissionalSaude profissional,
            Paciente paciente, LocalDateTime dataHora, Procedimento procedimento, List<String> linhas) {
        public boolean completo() {
            return especialidade != null && unidade != null && profissional != null
                    && paciente != null && dataHora != null && procedimento != null;
        }
    }

    /** Resolve todas as informações do agendamento a partir do registro e monta as linhas do diagnóstico. */
    public Avaliacao avaliar(Siresp s) {
        List<String> linhas = new ArrayList<>();

        String codEsp = trim(s.getIdEspecialidade());
        Especialidade esp = codEsp.isEmpty() ? null
                : especialidadeRepository.findByCodigoIntegracao(codEsp).orElse(null);
        linhas.add(codEsp.isEmpty() ? "Especialidade: código (ID_ESPECIALIDADE) não informado no XML."
                : esp != null ? "Especialidade: OK — " + esp.getNome() + "."
                        : "Especialidade: não encontrada pelo código de integração (" + codEsp + ").");

        String codUni = trim(s.getCodUnidadeExecutante());
        Unidade uni = codUni.isEmpty() ? null : unidadeRepository.findByCodigoIntegracao(codUni).orElse(null);
        linhas.add(codUni.isEmpty() ? "Unidade de saúde: código (COD_UNIDADE_EXECUTANTE) não informado no XML."
                : uni != null ? "Unidade de saúde: OK — " + uni.getNome() + "."
                        : "Unidade de saúde: não encontrada pelo código de integração (" + codUni + ").");

        String codProf = trim(s.getIdProfissional());
        ProfissionalSaude prof = codProf.isEmpty() ? null
                : profissionalRepository.findByCodigoIntegracao(codProf).orElse(null);
        linhas.add(codProf.isEmpty() ? "Profissional: código (ID_PROFISSIONAL) não informado no XML."
                : prof != null ? "Profissional: OK — " + prof.getNome() + "."
                        : "Profissional: não encontrado pelo código de integração (" + codProf + ").");

        String codPac = trim(s.getCodPaciente());
        String cpf = digitos(trim(s.getCpf()));
        Paciente pac = null;
        String via = null;
        if (!codPac.isEmpty()) {
            pac = pacienteRepository.findByCodigoIntegracao(codPac).orElse(null);
            via = pac != null ? "código de integração" : null;
        }
        if (pac == null && cpf.length() == 11) {
            pac = pacienteRepository.findByCpf(cpf).orElse(null);
            via = pac != null ? "CPF" : null;
        }
        linhas.add(pac != null ? "Paciente: OK — " + pac.getNome() + " (encontrado por " + via + ")."
                : configService.criar()
                        ? "Paciente: não encontrado por código de integração/CPF — será cadastrado na importação "
                                + "(cadastro automático HABILITADO)."
                        : "Paciente: não encontrado por código de integração/CPF — cadastre o paciente "
                                + "(cadastro automático DESABILITADO).");

        Optional<LocalDateTime> dataHora = parseDataHora(s);
        linhas.add(dataHora.map(d -> "Data/hora: OK — " + d.format(DATA_HORA_FMT) + ".")
                .orElse("Data/hora: inválida ou ausente (DATA_AGENDA / HOR_INI)."));

        // Procedimento (o XML do CROSS não traz procedimento): na CONSULTA vem do vínculo da ESPECIALIDADE;
        // no EXAME vem do cadastro de EXAME (casado pelo ID_EXAME → codigoIntegracao).
        Procedimento proc;
        if (s.getTipoRegistro() == TipoRegistroSiresp.EXAME) {
            String codExame = trim(s.getIdExame());
            Exame exame = codExame.isEmpty() ? null : exameRepository.findByCodigoIntegracao(codExame).orElse(null);
            proc = exame == null ? null : exame.getProcedimento();
            linhas.add(codExame.isEmpty()
                    ? "Exame/Procedimento: código do exame (ID_EXAME) não informado no XML."
                    : exame == null
                            ? "Exame/Procedimento: exame não encontrado pelo código de integração (" + codExame
                                    + ") — cadastre o exame e vincule um procedimento."
                            : proc == null
                                    ? "Exame/Procedimento: o exame \"" + exame.getNome()
                                            + "\" não tem procedimento vinculado — vincule-o no cadastro de Exame."
                                    : "Exame/Procedimento: OK — " + proc.getNome() + " (vinculado ao exame "
                                            + exame.getNome() + ").");
        } else {
            proc = esp == null ? null : esp.getProcedimento();
            linhas.add(proc != null
                    ? "Procedimento: OK — " + proc.getNome() + " (vinculado à especialidade)."
                    : esp == null
                            ? "Procedimento: depende da especialidade — resolva a especialidade primeiro."
                            : "Procedimento: a especialidade \"" + esp.getNome()
                                    + "\" não tem procedimento vinculado — vincule-o no cadastro da Especialidade.");
        }

        return new Avaliacao(esp, uni, prof, pac, dataHora.orElse(null), proc, linhas);
    }

    /** Texto inicial do diagnóstico (sem criar nada) — usado para popular o log na importação. */
    public String logInicial(Siresp s) {
        return montarLog(avaliar(s), null);
    }

    /**
     * Processa o registro conforme a MOVIMENTAÇÃO (TIPO_CONSULTA/TIPO_EXAME): agendamento (cria), cancelamento
     * (cancela o horário existente) ou transferência (cancela o de origem e cria o novo). Atualiza o log e devolve o
     * registro salvo. Registro já concluído ({@code agendamentoId} preenchido) não reprocessa (409).
     */
    public Siresp processarRegistro(Long sirespId, Long usuarioId) {
        Siresp s = repository.findById(sirespId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));

        // Regra de negócio: registro já concluído (agendado/cancelado/transferido) — não reprocessa (o front bloqueia).
        if (s.getAgendamentoId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registro já concluído (horário #" + s.getAgendamentoId() + "); não é possível reprocessar.");
        }

        return switch (s.getTipoMovimento()) {
            case CANCELAMENTO -> processarCancelamento(s, usuarioId);
            case TRANSFERENCIA -> processarTransferencia(s, usuarioId);
            default -> processarAgendamento(s, usuarioId);
        };
    }

    /** AGENDAMENTO: avalia e cria o horário (dedup) se estiver tudo completo. Não cria nada pela metade. */
    private Siresp processarAgendamento(Siresp s, Long usuarioId) {
        Avaliacao a = avaliar(s);
        if (!a.completo()) {
            s.setLogIntegracao(montarLog(a, null));
            return repository.save(s);
        }
        Criacao c = criarOuReaproveitar(s, a, usuarioId);
        s.setAgendamentoId(c.agendamentoId());
        preencherFaltantes(s, a.paciente(), a.especialidade(), a.profissional(), a.unidade(), a.dataHora(), null);
        s.setLogIntegracao(montarLog(a, c.nota()));
        Siresp salvo = repository.save(s);
        // Notifica o paciente/responsáveis (push + rastreio de entrega) — FORA de transação, como o cadastro manual.
        if (c.novo() != null) {
            entregaService.notificarNovoAgendamento(c.novo());
        }
        return salvo;
    }

    /** CANCELAMENTO: acha o horário pelo código (ID_AGE_*_HOR) e cancela (CANCELADO_PELA_UNIDADE) + notifica. */
    private Siresp processarCancelamento(Siresp s, Long usuarioId) {
        String chave = codigoHorario(s);
        String motivo = sufixoMotivo(MOTIVO_CANCELAMENTO, s);
        if (chave.isEmpty()) {
            s.setLogIntegracao(carimbar("Cancelamento: código do horário não informado no XML — nada a cancelar."));
            return repository.save(s);
        }
        Horario h = agendamentoRepository.findFirstByCodigoIntegracaoAndTipoAtendimento(chave, tipoAtendimento(s))
                .orElse(null);
        if (h == null) {
            s.setLogIntegracao(carimbar("Cancelamento: horário " + chave + " não encontrado — talvez o agendamento "
                    + "não tenha sido importado. Precisa de revisão."));
            return repository.save(s); // agendamentoId null → "Precisa de revisão"
        }
        // Achou o horário → preenche os dados de exibição do registro (o XML de cancelamento é enxuto) p/ a tela/busca.
        preencherFaltantes(s, h.getPaciente(), h.getEspecialidade(), h.getProfissionalSaude(), h.getUnidadeSaude(),
                h.getDataHora(), h.getHoraFim());
        ResultadoCancelamento r = cancelarHorario(h, usuarioId);
        String nota;
        switch (r) {
            case CANCELADO -> {
                s.setAgendamentoId(h.getId());
                nota = "horário #" + h.getId() + " cancelado" + motivo + ".";
            }
            case JA_CANCELADO -> {
                s.setAgendamentoId(h.getId());
                nota = "horário #" + h.getId() + " já estava cancelado" + motivo + ".";
            }
            // NAO_CANCELAVEL: o horário já ocorreu (presença/falta) — não cancela nem notifica; fica p/ revisão humana.
            default -> nota = "horário #" + h.getId() + " NÃO cancelado — já concluído ("
                    + h.getStatusAgendamento().getDescricao() + "); verifique. Precisa de revisão.";
        }
        s.setLogIntegracao(carimbar("Cancelamento: " + nota));
        Siresp salvo = repository.save(s);
        if (r == ResultadoCancelamento.CANCELADO) {
            notificarCancelamento(h);
        }
        return salvo;
    }

    /**
     * TRANSFERÊNCIA: cancela o horário de ORIGEM e cria o NOVO (só quando o novo estiver completo). O XML de
     * transferência é ENXUTO (traz só o COD_PACIENTE, sem nome/CPF), então QUEM é o paciente vem do horário de
     * ORIGEM (referenciado por ID_AGE_*_HOR_ORIGEM), que já existe no sistema — o paciente é o mesmo antes e depois.
     * As demais peças do NOVO agendamento (especialidade/profissional/unidade/data/hora) vêm do próprio XML, pois uma
     * transferência pode mudá-las (troca de agenda/profissional). Sem a origem no sistema não há o que transferir.
     */
    private Siresp processarTransferencia(Siresp s, Long usuarioId) {
        Avaliacao a = avaliar(s);

        // Localiza o horário de ORIGEM (só leitura aqui; o cancelamento é feito depois, e só se o novo estiver
        // completo). Se a origem for IGUAL ao destino (transferência degenerada p/ o mesmo horário), não mexe nela —
        // senão cancelaria o próprio slot que vai ser reaproveitado como o novo.
        String origem = codigoHorarioOrigem(s);
        boolean mesmoSlot = !origem.isEmpty() && origem.equals(codigoHorario(s));
        Horario antigo = (origem.isEmpty() || mesmoSlot) ? null
                : agendamentoRepository.findFirstByCodigoIntegracaoAndTipoAtendimento(origem, tipoAtendimento(s)).orElse(null);
        // Paciente não resolvido pelo XML enxuto → herda do horário de origem (é o mesmo paciente da transferência).
        if (a.paciente() == null && antigo != null && antigo.getPaciente() != null) {
            a = new Avaliacao(a.especialidade(), a.unidade(), a.profissional(), antigo.getPaciente(),
                    a.dataHora(), a.procedimento(), a.linhas());
        }

        // Regra: numa transferência o paciente NÃO muda. Se o XML informou/resolveu um paciente e ele é DIFERENTE do
        // paciente do horário de origem, bloqueia — não transfere o agendamento de outra pessoa. NÃO cancela a origem.
        if (a.paciente() != null && antigo != null && antigo.getPaciente() != null
                && !a.paciente().getId().equals(antigo.getPaciente().getId())) {
            s.setLogIntegracao(carimbar("Transferência: Paciente de origem é divergente do paciente da transferência"
                    + " (origem: " + antigo.getPaciente().getNome() + " #" + antigo.getPaciente().getId()
                    + "; transferência: " + a.paciente().getNome() + " #" + a.paciente().getId()
                    + ") — é preciso ser o mesmo paciente. O horário de origem NÃO foi cancelado."));
            return repository.save(s);
        }

        if (!a.completo()) {
            // Não faz pela metade: se o novo agendamento está incompleto, NÃO cancela o de origem.
            String motivo = (a.paciente() == null && !origem.isEmpty() && antigo == null)
                    ? "Transferência: o XML não traz os dados do paciente e o horário de origem (" + origem
                            + ") não existe no sistema — importe o agendamento original primeiro. "
                            + "O horário de origem NÃO foi cancelado."
                    : "Transferência: novo agendamento incompleto — o horário de origem NÃO foi cancelado.";
            s.setLogIntegracao(montarLog(a, motivo));
            return repository.save(s);
        }
        // 1) cancela o horário de ORIGEM (se achar).
        ResultadoCancelamento rc = antigo == null ? null : cancelarHorario(antigo, usuarioId);
        String notaOrigem;
        if (mesmoSlot) {
            notaOrigem = "origem igual ao destino — nada a cancelar;";
        } else if (origem.isEmpty()) {
            notaOrigem = "origem não informada;";
        } else if (antigo == null) {
            notaOrigem = "horário de origem (" + origem + ") não encontrado;";
        } else {
            notaOrigem = switch (rc) {
                case CANCELADO -> "horário de origem #" + antigo.getId() + " cancelado;";
                case JA_CANCELADO -> "horário de origem #" + antigo.getId() + " já estava cancelado;";
                default -> "horário de origem #" + antigo.getId() + " não cancelado (já concluído);";
            };
        }
        // 2) cria o NOVO horário (mesma lógica/dedup do agendamento).
        Criacao c = criarOuReaproveitar(s, a, usuarioId);
        s.setAgendamentoId(c.agendamentoId());
        // Preenche o que o XML de transferência não traz (nome/CPF do paciente) a partir do que foi resolvido.
        preencherFaltantes(s, a.paciente(), a.especialidade(), a.profissional(), a.unidade(), a.dataHora(), null);
        s.setLogIntegracao(montarLog(a, "Transferência" + sufixoMotivo(MOTIVO_TRANSFERENCIA, s) + ": "
                + notaOrigem + " " + c.nota()));
        Siresp salvo = repository.save(s);
        if (rc == ResultadoCancelamento.CANCELADO) {
            notificarCancelamento(antigo);
        }
        if (c.novo() != null) {
            entregaService.notificarNovoAgendamento(c.novo());
        }
        return salvo;
    }

    /** Resultado da criação/reaproveitamento do horário: id vinculado, o horário novo (ou null se já existia), nota. */
    private record Criacao(Long agendamentoId, Horario novo, String nota) {
    }

    /**
     * Cria o horário ou reaproveita o já existente (dedup em três níveis), devolvendo o id + o horário novo (ou null)
     * + a nota para o log. Usado pelo agendamento e pela criação do novo horário da transferência.
     */
    private Criacao criarOuReaproveitar(Siresp s, Avaliacao a, Long usuarioId) {
        // Dedup: (0) horário já gravado com este código+tipo; (1) outro registro SIRESP com o mesmo código que já
        // gerou horário; (2) fallback pela CHAVE NATURAL (só quando o código está vazio — caso órfão).
        boolean exame = s.getTipoRegistro() == TipoRegistroSiresp.EXAME;
        Long agendamentoId = null;
        String chave = codigoHorario(s);
        if (!chave.isEmpty()) {
            agendamentoId = agendamentoRepository
                    .findFirstByCodigoIntegracaoAndTipoAtendimento(chave, tipoAtendimento(s))
                    .map(Horario::getId).orElse(null);
        }
        if (agendamentoId == null && !chave.isEmpty()) {
            agendamentoId = (exame
                    ? repository.findFirstByIdAgeExameHorAndAgendamentoIdIsNotNull(chave)
                    : repository.findFirstByIdAgeConsultaHorAndAgendamentoIdIsNotNull(chave))
                    .map(Siresp::getAgendamentoId).orElse(null);
        }
        // Fallback pela CHAVE NATURAL (paciente + profissional + data/hora): vale quando o código está VAZIO (órfão,
        // qualquer tipo) OU quando é CONSULTA (um paciente não tem 2 consultas com o mesmo profissional no mesmo
        // minuto → dedup seguro). NÃO vale p/ EXAME com código: uma "associação"/painel tem vários exames no mesmo
        // slot com códigos diferentes — dedupar por chave natural perderia exames.
        if (agendamentoId == null && (chave.isEmpty() || !exame)) {
            // IGNORA horários CANCELADOS: um slot cancelado não é "duplicata" de uma marcação nova. Sem isso, uma
            // transferência de consulta que cancela a origem e recria no MESMO paciente/profissional/instante
            // reaproveitaria a própria origem recém-cancelada (nenhum horário novo criado, só a notif. de cancelamento).
            agendamentoId = agendamentoRepository
                    .buscarPorChaveNatural(a.paciente(), a.profissional(), a.dataHora(), tipoAtendimento(s))
                    .stream().filter(SirespAgendamentoService::ativoParaDedup).findFirst().map(Horario::getId).orElse(null);
        }
        if (agendamentoId != null) {
            return new Criacao(agendamentoId, null,
                    "Horário #" + agendamentoId + " já existente para este registro (não duplicado).");
        }
        Horario novo = criar(a, s, usuarioId);
        Agenda ag = novo.getAgenda();
        String rotuloCodigo = exame ? "ID_AGE_EXAME " : "ID_AGE_CONSULTA ";
        String agendaNota = ag.getCodigoIntegracao() == null
                ? "Agenda #" + ag.getId() + " criada"
                : "Agenda #" + ag.getId() + " (" + rotuloCodigo + ag.getCodigoIntegracao() + ")";
        return new Criacao(novo.getId(), novo, agendaNota + " · horário #" + novo.getId() + " criado.");
    }

    /** Desfecho de um pedido de cancelamento sobre um horário. */
    private enum ResultadoCancelamento { CANCELADO, JA_CANCELADO, NAO_CANCELAVEL }

    /**
     * Cancela o horário (CANCELADO_PELA_UNIDADE) + log de auditoria. SÓ cancela quando ainda está ATIVO
     * (aguardando/confirmado): já cancelado → {@code JA_CANCELADO} (sem alterar); já concluído com presença/falta →
     * {@code NAO_CANCELAVEL} (NÃO sobrescreve o comparecimento/falta nem notifica).
     */
    private ResultadoCancelamento cancelarHorario(Horario h, Long usuarioId) {
        StatusAgendamento antes = h.getStatusAgendamento();
        if (antes == StatusAgendamento.CANCELADO_PELA_UNIDADE || antes == StatusAgendamento.CANCELADO_PELO_PACIENTE) {
            return ResultadoCancelamento.JA_CANCELADO;
        }
        if (antes != StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE
                && antes != StatusAgendamento.PACIENTE_CONFIRMOU) {
            return ResultadoCancelamento.NAO_CANCELAVEL; // presença/falta: histórico protegido
        }
        h.setStatusAgendamento(StatusAgendamento.CANCELADO_PELA_UNIDADE);
        Horario salvo = agendamentoRepository.save(h);
        agendamentoLogService.registrarDaUnidade(salvo, antes, StatusAgendamento.CANCELADO_PELA_UNIDADE, usuarioId);
        return ResultadoCancelamento.CANCELADO;
    }

    /** Horário NÃO cancelado — único que serve de alvo p/ dedup por chave natural (ver {@link #criarOuReaproveitar}). */
    private static boolean ativoParaDedup(Horario h) {
        StatusAgendamento st = h.getStatusAgendamento();
        return st != StatusAgendamento.CANCELADO_PELA_UNIDADE && st != StatusAgendamento.CANCELADO_PELO_PACIENTE;
    }

    /** Push direto ao paciente avisando o cancelamento (sem rastreio de entrega, como o aviso de falta). */
    private void notificarCancelamento(Horario h) {
        pushService.notificarPaciente(h.getPaciente().getId(), FuncionalidadeApp.AGENDAMENTOS,
                "Agendamento cancelado",
                "Seu agendamento de " + h.getEspecialidade().getNome() + " em "
                        + h.getDataHora().format(DATA_HORA_FMT) + " foi cancelado.",
                Map.<String, Object>of("agendamentoId", h.getId()));
    }

    /** Código do HORÁRIO DE ORIGEM (transferência) conforme o tipo (ID_AGE_EXAME_HOR_ORIGEM/ID_AGE_CONSULTA_HOR_ORIGEM). */
    private static String codigoHorarioOrigem(Siresp s) {
        return trim(s.getTipoRegistro() == TipoRegistroSiresp.EXAME
                ? s.getIdAgeExameHorOrigem() : s.getIdAgeConsultaHorOrigem());
    }

    /** " (motivo: X)" a partir do ID_MOTIVO do registro e da lista do CROSS, ou "" quando não reconhecido. */
    private static String sufixoMotivo(Map<String, String> lista, Siresp s) {
        String nome = lista.get(trim(s.getIdMotivo()));
        return nome == null ? "" : " (motivo: " + nome + ")";
    }

    /** Acrescenta o carimbo "Processado em ..." a um log construído manualmente (cancelamento). */
    private static String carimbar(String texto) {
        return texto + "\nProcessado em " + LocalDateTime.now().format(PROCESSADO_FMT) + ".";
    }

    /**
     * Preenche o MÁXIMO de campos do registro que vieram VAZIOS do XML, a partir do que o sistema resolveu pelos
     * códigos de integração (paciente/profissional/especialidade/unidade/horário encontrados) — típico de
     * cancelamento/transferência, cujo XML é enxuto, mas também completa qualquer campo ausente num agendamento.
     * Preenche o PACIENTE completo (demografia, documentos, endereço, telefones e contato/responsável) e o
     * PROFISSIONAL completo (código de integração, documento e conselho), além de especialidade, unidade executante e
     * data/hora. NUNCA sobrescreve o que o XML já trouxe (só preenche vazio). Marca {@code dadosResolvidos} se
     * preencher algo (o detalhe avisa, e o registro passa a ser pesquisável/reenviável por esses campos). Peças
     * nulas são ignoradas.
     */
    private static void preencherFaltantes(Siresp s, Paciente p, Especialidade esp, ProfissionalSaude prof,
            Unidade unidade, LocalDateTime dataHora, LocalTime horaFim) {
        boolean preencheu = false;

        // --- Paciente (completo) ---
        if (p != null) {
            preencheu |= setSeVazio(s::getCodPaciente, s::setCodPaciente, p.getCodigoIntegracao());
            preencheu |= setSeVazio(s::getNomePaciente, s::setNomePaciente, p.getNome());
            preencheu |= setSeVazio(s::getCpf, s::setCpf, p.getCpf());
            preencheu |= setSeVazio(s::getSexo, s::setSexo, sexoSiresp(p.getSexo()));
            preencheu |= setSeVazio(s::getDtNascimento, s::setDtNascimento, iso(p.getDataNascimento()));
            preencheu |= setSeVazio(s::getRg, s::setRg, p.getRg());
            preencheu |= setSeVazio(s::getNomeMae, s::setNomeMae, p.getNomeMae());
            preencheu |= setSeVazio(s::getNomePai, s::setNomePai, p.getNomePai());
            preencheu |= setSeVazio(s::getEndereco, s::setEndereco, p.getRua());
            preencheu |= setSeVazio(s::getEnderecoNumero, s::setEnderecoNumero, p.getNumero());
            preencheu |= setSeVazio(s::getBairro, s::setBairro, p.getBairro());
            preencheu |= setSeVazio(s::getMunicipio, s::setMunicipio, p.getMunicipio());
            preencheu |= setSeVazio(s::getUf, s::setUf, p.getUf());
            preencheu |= setSeVazio(s::getCep, s::setCep, p.getCep());
            preencheu |= setSeVazio(s::getEmail, s::setEmail, p.getEmail());
            preencheu |= setSeVazio(s::getNumCns, s::setNumCns, p.getCns());
            preencheu |= setSeVazio(s::getNumProntuario, s::setNumProntuario, p.getProntuario());
            preencheu |= preencherTelefones(s, p.getTelefonesAdicionais());
            // Contato = 1º responsável ativo (nome + telefone).
            Responsavel resp = primeiroResponsavelAtivo(p);
            if (resp != null) {
                preencheu |= setSeVazio(s::getContatoNome, s::setContatoNome, resp.getNome());
                preencheu |= preencherTelefone(s, s::getContatoTel, s::setContatoTelDdd, s::setContatoTel,
                        resp.getTelefone());
            }
        }

        // --- Profissional (completo) ---
        if (prof != null) {
            preencheu |= setSeVazio(s::getNomeProfissional, s::setNomeProfissional, prof.getNome());
            preencheu |= setSeVazio(s::getIdProfissional, s::setIdProfissional, prof.getCodigoIntegracao());
            preencheu |= setSeVazio(s::getDocProfissional, s::setDocProfissional, prof.getNumeroConselho());
            preencheu |= setSeVazio(s::getOrigem, s::setOrigem,
                    prof.getConselho() == null ? null : prof.getConselho().getSigla());
        }

        // --- Especialidade e unidade executante (nome + código resolvidos) ---
        if (esp != null) {
            preencheu |= setSeVazio(s::getNomeEspecialidade, s::setNomeEspecialidade, esp.getNome());
            preencheu |= setSeVazio(s::getIdEspecialidade, s::setIdEspecialidade, esp.getCodigoIntegracao());
        }
        if (unidade != null) {
            preencheu |= setSeVazio(s::getCodUnidadeExecutante, s::setCodUnidadeExecutante,
                    unidade.getCodigoIntegracao());
        }

        // --- Data/hora ---
        if (dataHora != null) {
            preencheu |= setSeVazio(s::getDataAgenda, s::setDataAgenda, dataHora.toLocalDate().toString());
            preencheu |= setSeVazio(s::getHorIni, s::setHorIni, dataHora.toLocalTime().format(HORA_HMS));
        }
        if (horaFim != null) {
            preencheu |= setSeVazio(s::getHorFim, s::setHorFim, horaFim.format(HORA_HMS));
        }

        if (preencheu) {
            s.setDadosResolvidos(true);
        }
    }

    /** Grava {@code valor} via {@code setter} só quando o campo atual está vazio e o valor é não-vazio. Devolve se gravou. */
    private static boolean setSeVazio(Supplier<String> getter, Consumer<String> setter, String valor) {
        if (valor == null || valor.isBlank() || !vazio(getter.get())) {
            return false;
        }
        setter.accept(valor);
        return true;
    }

    /**
     * Distribui os telefones do paciente (somente dígitos, com DDD) nos campos do SIRESP: 11 dígitos = CELULAR,
     * 10 = RESIDENCIAL; se o "balde" natural já estiver cheio, usa o outro. Telefones sem DDD (menos de 10 dígitos)
     * são ignorados (não dá para separar DDD com segurança). Só preenche campos vazios.
     */
    private static boolean preencherTelefones(Siresp s, List<String> telefones) {
        if (telefones == null) {
            return false;
        }
        boolean preencheu = false;
        for (String bruto : telefones) {
            String tel = digitos(trim(bruto));
            if (tel.length() < 10) {
                continue;
            }
            String ddd = tel.substring(0, 2);
            String num = tel.substring(2);
            // Não duplica entre os campos: se este número já está no celular OU no residencial (inclusive trazido
            // pelo XML, ou repetido na lista do paciente), pula — senão o mesmo número cairia nos dois, um rotulado
            // errado, e seria reenviado assim no XML reconstruído.
            if (mesmoTelefone(s.getTelCelularDdd(), s.getTelCelular(), tel, num)
                    || mesmoTelefone(s.getTelResDdd(), s.getTelRes(), tel, num)) {
                continue;
            }
            boolean movel = tel.length() >= 11; // 11 dígitos = celular (9 + 8)
            if (movel) {
                if (vazio(s.getTelCelular())) {
                    s.setTelCelularDdd(ddd);
                    s.setTelCelular(num);
                    preencheu = true;
                } else if (vazio(s.getTelRes())) {
                    s.setTelResDdd(ddd);
                    s.setTelRes(num);
                    preencheu = true;
                }
            } else {
                if (vazio(s.getTelRes())) {
                    s.setTelResDdd(ddd);
                    s.setTelRes(num);
                    preencheu = true;
                } else if (vazio(s.getTelCelular())) {
                    s.setTelCelularDdd(ddd);
                    s.setTelCelular(num);
                    preencheu = true;
                }
            }
        }
        return preencheu;
    }

    /**
     * Um telefone já gravado (par DDD+número do SIRESP) representa o MESMO número do candidato? Compara por dígitos,
     * tolerando o XML ter gravado com ou sem DDD embutido no número. Vazio → false.
     */
    private static boolean mesmoTelefone(String dddGravado, String numGravado, String telCompleto, String num) {
        String numG = digitos(trim(numGravado));
        if (numG.isEmpty()) {
            return false;
        }
        String completoG = digitos(trim(dddGravado)) + numG;
        return completoG.equals(telCompleto) || numG.equals(num);
    }

    /** Preenche um par (DDD, número) a partir de um telefone só-dígitos (DDD = 2 primeiros), só quando o número está vazio. */
    private static boolean preencherTelefone(Siresp s, Supplier<String> getNum, Consumer<String> setDdd,
            Consumer<String> setNum, String bruto) {
        String tel = digitos(trim(bruto));
        if (tel.length() < 10 || !vazio(getNum.get())) {
            return false;
        }
        setDdd.accept(tel.substring(0, 2));
        setNum.accept(tel.substring(2));
        return true;
    }

    /** 1º responsável ATIVO (com nome) do paciente, para preencher o contato do SIRESP; {@code null} se não houver. */
    private static Responsavel primeiroResponsavelAtivo(Paciente p) {
        if (p.getResponsaveis() == null) {
            return null;
        }
        return p.getResponsaveis().stream()
                .filter(r -> r.isAtivo() && !vazio(r.getNome()))
                .findFirst().orElse(null);
    }

    /** Sexo do POP → SEXO do SIRESP (M/F/I, mesma convenção da importação). OUTRO/NAO_INFORMADO → "I". Null → null. */
    private static String sexoSiresp(Sexo sexo) {
        if (sexo == null) {
            return null;
        }
        return switch (sexo) {
            case MASCULINO -> "M";
            case FEMININO -> "F";
            default -> "I";
        };
    }

    /** LocalDate → ISO (yyyy-MM-dd), mesmo formato dos demais campos de data preenchidos pelo sistema. */
    private static String iso(LocalDate data) {
        return data == null ? null : data.toString();
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }

    /**
     * Cria o Horário (marcação, status inicial AGUARDANDO_CONFIRMACAO_PACIENTE) + 1ª linha do log, sob a Agenda (slot)
     * do código do CROSS conforme o tipo: reaproveita a agenda já existente com esse código ou cria uma nova. Assim um
     * mesmo slot com vários pacientes vira UMA agenda com N horários. Vincula os códigos de rastreio: a agenda ao
     * ID_AGE_CONSULTA/ID_AGE_EXAME (+ nome) e o horário ao ID_AGE_CONSULTA_HOR/ID_AGE_EXAME_HOR.
     */
    private Horario criar(Avaliacao a, Siresp s, Long usuarioId) {
        TipoAtendimento tipo = tipoAtendimento(s);
        Agenda agenda = obterOuCriarAgenda(a, codigoAgenda(s), nomeAgenda(s), tipo);

        String codHor = codigoHorario(s);
        Horario ag = new Horario();
        ag.setAgenda(agenda);
        ag.setCodigoIntegracao(nuloSeVazio(codHor));
        // Tipo pareado com o código (consistente com o índice único composto): só quando há código.
        ag.setTipoAtendimento(codHor.isEmpty() ? null : tipo);
        ag.setDataHora(a.dataHora());
        ag.setHoraFim(parseHora(trim(s.getHorFim())));
        ag.setPaciente(a.paciente());
        ag.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Horario salvo = agendamentoRepository.save(ag);
        agendamentoLogService.registrarDaUnidade(salvo, null, StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE,
                usuarioId);
        return salvo;
    }

    /**
     * Encontra a Agenda (slot) deste {@code ID_AGE_CONSULTA} ou cria uma nova (gravando o código + o nome do CROSS).
     * Sem código (ID_AGE_CONSULTA vazio) cria sempre uma agenda avulsa (sem agrupar), preservando o comportamento de
     * 1 agenda por registro. A agenda existente é reaproveitada COMO ESTÁ — a correção de dados do slot é feita pela
     * tela da agenda.
     */
    private Agenda obterOuCriarAgenda(Avaliacao a, String codigoAgenda, String nomeAgenda, TipoAtendimento tipo) {
        if (!codigoAgenda.isEmpty()) {
            // Lookup por (código + TIPO) — os id-spaces do CROSS podem colidir, então o código sozinho mesclaria tipos.
            Agenda existente = agendaRepository.findFirstByCodigoIntegracaoAndTipoAtendimento(codigoAgenda, tipo)
                    .orElse(null);
            if (existente != null) {
                return existente;
            }
        }
        Agenda agenda = new Agenda();
        agenda.setCodigoIntegracao(nuloSeVazio(codigoAgenda));
        // Tipo pareado com o código (consistente com o índice único composto): só quando há código.
        agenda.setTipoAtendimento(codigoAgenda.isEmpty() ? null : tipo);
        agenda.setNome(nomeAgenda.isEmpty() ? null : limitar(nomeAgenda, 255));
        agenda.setData(a.dataHora().toLocalDate());
        agenda.setEspecialidade(a.especialidade());
        agenda.setProfissionalSaude(a.profissional());
        agenda.setProcedimento(a.procedimento());
        agenda.setUnidadeSaude(a.unidade());
        return agendaRepository.save(agenda);
    }

    /** Tipo de atendimento (Agenda/Horário) a partir do tipo do registro SIRESP — diferencia os códigos do CROSS. */
    private static TipoAtendimento tipoAtendimento(Siresp s) {
        return s.getTipoRegistro() == TipoRegistroSiresp.EXAME ? TipoAtendimento.EXAME : TipoAtendimento.CONSULTA;
    }

    /** Código do HORÁRIO no CROSS conforme o tipo (ID_AGE_EXAME_HOR p/ exame, ID_AGE_CONSULTA_HOR p/ consulta). */
    private static String codigoHorario(Siresp s) {
        return trim(s.getTipoRegistro() == TipoRegistroSiresp.EXAME ? s.getIdAgeExameHor() : s.getIdAgeConsultaHor());
    }

    /** Código da AGENDA no CROSS conforme o tipo (ID_AGE_EXAME p/ exame, ID_AGE_CONSULTA p/ consulta). */
    private static String codigoAgenda(Siresp s) {
        return trim(s.getTipoRegistro() == TipoRegistroSiresp.EXAME ? s.getIdAgeExame() : s.getIdAgeConsulta());
    }

    /** Nome da AGENDA no CROSS conforme o tipo (AGE_EXAME_NOME p/ exame, AGE_CONSULTA_NOME p/ consulta). */
    private static String nomeAgenda(Siresp s) {
        return trim(s.getTipoRegistro() == TipoRegistroSiresp.EXAME ? s.getAgeExameNome() : s.getAgeConsultaNome());
    }

    /** Null quando vazio (os códigos do CROSS são gravados como vieram — chaves de rastreio, não truncadas). */
    private static String nuloSeVazio(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /**
     * Corta o NOME da agenda (rótulo) no tamanho da coluna — pode ser um texto longo do CROSS. Só vale para o nome:
     * os códigos de integração NÃO são truncados (truncar uma chave de rastreio desalinharia dedup × armazenamento).
     * Vazio → null.
     */
    private static String limitar(String v, int max) {
        if (v == null || v.isEmpty()) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private String montarLog(Avaliacao a, String nota) {
        StringBuilder sb = new StringBuilder(a.completo()
                ? "Tudo OK — especialidade, unidade, profissional, paciente, data/hora e procedimento encontrados."
                : String.join("\n", a.linhas()));
        if (nota != null && !nota.isBlank()) {
            sb.append("\n").append(nota);
        }
        // Carimbo do último processamento (fica no fim para não quebrar a detecção do "Tudo OK" no início).
        sb.append("\nProcessado em ").append(LocalDateTime.now().format(PROCESSADO_FMT)).append(".");
        return sb.toString();
    }

    /** Combina DATA_AGENDA + HOR_INI em LocalDateTime (hora local BR). Vazio/ inválido/ data-sentinela → empty. */
    private Optional<LocalDateTime> parseDataHora(Siresp s) {
        LocalDate data = parseData(trim(s.getDataAgenda()));
        LocalTime hora = parseHora(trim(s.getHorIni()));
        if (data == null || hora == null || data.getYear() < 1900) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(data, hora));
    }

    private static LocalDate parseData(String v) {
        if (v.isEmpty()) {
            return null;
        }
        String dataParte = v.contains("T") ? v.substring(0, v.indexOf('T')) : v;
        for (DateTimeFormatter f : DATAS) {
            try {
                return LocalDate.parse(dataParte, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalTime parseHora(String v) {
        if (v.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : HORAS) {
            try {
                return LocalTime.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String digitos(String v) {
        return v.replaceAll("\\D", "");
    }
}
