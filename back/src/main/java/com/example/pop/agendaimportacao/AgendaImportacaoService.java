package com.example.pop.agendaimportacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agenda;
import com.example.pop.agendamento.AgendaRepository;
import com.example.pop.agendamento.Horario;
import com.example.pop.agendamento.HorarioEntregaService;
import com.example.pop.agendamento.HorarioLogService;
import com.example.pop.agendamento.HorarioRepository;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.configuracaoagenda.ConfiguracaoAgendaRepository;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.Documentos;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteLogService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Importação de agenda por Excel. PARSEIA (via {@link AgendaPlanilhaParser}) e LOCALIZA os cadastros por
 * ID ou CÓDIGO (nunca por nome). Duas operações:
 * <ul>
 *   <li>{@link #preview} (Fase 1): devolve o {@link AgendaImportPreviewResponse} com o que foi encontrado +
 *       os erros — não grava nada.</li>
 *   <li>{@link #persistir} (Fase 2): re-valida no servidor e, se não houver erro, CRIA a Agenda + os Horários
 *       numa transação; a notificação ao paciente roda DEPOIS via {@link #notificarImportados}.</li>
 * </ul>
 *
 * <p>Cada cadastro tem colunas separadas por tipo de identificador e o cliente preenche exatamente uma:
 * Profissional/Especialidade por id interno OU código; Configuração da Agenda só por id; Paciente por id interno
 * OU prontuário OU código. A unidade executante é a do usuário logado ({@code unidadeId}). O status nasce sempre
 * "Aguardando confirmação do paciente".
 */
@Service
public class AgendaImportacaoService {

    private static final DateTimeFormatter DATA_SAIDA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<DateTimeFormatter> DATA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    private static final List<DateTimeFormatter> HORA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"));
    private static final StatusAgendamento STATUS_INICIAL = StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE;

    private final AgendaPlanilhaParser parser;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ConfiguracaoAgendaRepository configuracaoAgendaRepository;
    private final UnidadeRepository unidadeRepository;
    private final PacienteRepository pacienteRepository;
    private final AgendaRepository agendaRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioLogService logService;
    private final HorarioEntregaService entregaService;
    private final com.example.pop.paciente.VinculoPacienteUnidadeService vinculoService;
    private final PacienteLogService pacienteLogService;

    public AgendaImportacaoService(AgendaPlanilhaParser parser,
            ProfissionalSaudeRepository profissionalRepository,
            EspecialidadeRepository especialidadeRepository,
            ConfiguracaoAgendaRepository configuracaoAgendaRepository,
            UnidadeRepository unidadeRepository,
            PacienteRepository pacienteRepository,
            AgendaRepository agendaRepository,
            HorarioRepository horarioRepository,
            HorarioLogService logService,
            HorarioEntregaService entregaService,
            com.example.pop.paciente.VinculoPacienteUnidadeService vinculoService,
            PacienteLogService pacienteLogService) {
        this.parser = parser;
        this.profissionalRepository = profissionalRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.configuracaoAgendaRepository = configuracaoAgendaRepository;
        this.unidadeRepository = unidadeRepository;
        this.pacienteRepository = pacienteRepository;
        this.agendaRepository = agendaRepository;
        this.horarioRepository = horarioRepository;
        this.logService = logService;
        this.entregaService = entregaService;
        this.vinculoService = vinculoService;
        this.pacienteLogService = pacienteLogService;
    }

    /** Referência de cadastro para casar por id/código. {@code codigo} nulo = sem código de integração. */
    private record RefCadastro(Long id, String nome, String codigo) {
    }

    /** Resultado de uma resolução: o que mostrar ({@code campo}) + o valor resolvido para gravar ({@code valor}). */
    private record Campo<T>(CampoPreview campo, T valor) {
    }

    /** Agenda inteira resolvida: previews + valores para gravar. */
    private record AgendaResolvida(Campo<LocalDate> data, Campo<Long> profissional, Campo<Long> especialidade,
            Campo<Long> config, Campo<Long> unidade, CampoPreview nome, List<HorarioResolvido> horarios) {

        AgendaPreview preview() {
            return new AgendaPreview(data.campo(), profissional.campo(), especialidade.campo(),
                    config.campo(), unidade.campo(), nome);
        }
    }

    private record HorarioResolvido(int linha, Campo<Long> paciente, boolean criarPaciente, String prontuarioNovo,
            String codigoNovo, Campo<String> cpf, Campo<String> nome, Campo<String> nomeSocial,
            Campo<String> telefone, Campo<LocalDate> dataNascimento, Campo<LocalTime> inicio) {

        HorarioPreview preview() {
            return new HorarioPreview(linha, paciente.campo(), cpf.campo(), nome.campo(), nomeSocial.campo(),
                    telefone.campo(), dataNascimento.campo(), inicio.campo());
        }
    }

    /** O que a gravação devolve para o controller notificar fora da transação. */
    public record ResultadoImportacao(Long agendaId, List<Long> horarioIds) {
    }

    // ---------------- Fase 1: preview ----------------

    /** @param unidadeId unidade do usuário logado — é a unidade executante da agenda. */
    @Transactional(readOnly = true)
    public AgendaImportPreviewResponse preview(byte[] bytes, String arquivo, Long unidadeId) {
        AgendaResolvida r = resolver(bytes, unidadeId);
        List<HorarioPreview> horarios = r.horarios().stream().map(HorarioResolvido::preview).toList();
        return new AgendaImportPreviewResponse(arquivo, r.preview(), horarios, horarios.size(), totalErros(r));
    }

    // ---------------- Fase 2: gravação ----------------

    /**
     * Re-valida no servidor e, se não houver erro, CRIA a Agenda + os Horários numa transação. NÃO notifica aqui
     * (o POST à Expo roda fora de transação): o controller chama {@link #notificarImportados} depois do commit.
     */
    @Transactional
    public ResultadoImportacao persistir(byte[] bytes, Long unidadeId, Long uid) {
        AgendaResolvida r = resolver(bytes, unidadeId);
        if (totalErros(r) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Corrija os erros destacados antes de confirmar a importação.");
        }
        if (r.horarios().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A planilha não tem nenhum horário para importar.");
        }

        Agenda agenda = new Agenda();
        agenda.setData(r.data().valor());
        agenda.setProfissionalSaude(profissionalRepository.findById(r.profissional().valor())
                .orElseThrow(() -> sumiu("Profissional")));
        agenda.setEspecialidade(especialidadeRepository.findById(r.especialidade().valor())
                .orElseThrow(() -> sumiu("Especialidade")));
        agenda.setConfiguracaoAgenda(configuracaoAgendaRepository.findById(r.config().valor())
                .orElseThrow(() -> sumiu("Configuração da Agenda")));
        agenda.setUnidadeSaude(unidadeRepository.findById(r.unidade().valor())
                .orElseThrow(() -> sumiu("Unidade")));
        String nome = r.nome().valor();
        if (nome != null && !nome.isBlank()) {
            agenda.setNome(nome.trim());
        }
        Agenda agendaSalva = agendaRepository.save(agenda);

        List<Long> horarioIds = new ArrayList<>();
        // Dedup dentro da própria importação: duas linhas do MESMO CPF novo criam só um paciente (reusa o 1º).
        Map<String, Paciente> criadosPorCpf = new HashMap<>();
        for (HorarioResolvido hr : r.horarios()) {
            Paciente paciente = obterOuCriarPaciente(hr, r.unidade().valor(), uid, criadosPorCpf);
            Horario h = new Horario();
            h.setAgenda(agendaSalva);
            h.setPaciente(paciente);
            h.setDataHora(r.data().valor().atTime(hr.inicio().valor()));
            h.setStatusAgendamento(STATUS_INICIAL);
            // Dados do paciente informados na linha da planilha — gravados junto ao horário (não alteram o cadastro).
            h.setImportacaoCpf(hr.cpf().valor());
            h.setImportacaoNome(hr.nome().valor());
            String nomeSocial = hr.nomeSocial().valor();
            h.setImportacaoNomeSocial(nomeSocial);
            h.setImportacaoUtilizaNomeSocial(nomeSocial != null && !nomeSocial.isBlank());
            h.setImportacaoTelefone(hr.telefone().valor());
            h.setImportacaoDataNascimento(hr.dataNascimento().valor());
            Horario salvo = horarioRepository.save(h);
            logService.registrarDaUnidade(salvo, null, STATUS_INICIAL, uid);
            horarioIds.add(salvo.getId());
            // Importar agendamento dá ao paciente acesso à unidade (perfil/feed/chat/SAU) — idempotente.
            vinculoService.garantir(paciente.getId(), r.unidade().valor());
        }
        return new ResultadoImportacao(agendaSalva.getId(), horarioIds);
    }

    /**
     * Devolve o paciente da linha para gravar o horário: o existente (localizado por identificador ou vinculado
     * pelo CPF) ou, quando a regra decidiu criar, um paciente NOVO com os dados da linha (nome, nome social +
     * "utiliza", CPF, data de nascimento, telefone, prontuário/código) — criado inativo (a ativação do app é
     * feita depois) e auditado (autor IMPORTACAO_AGENDA). Dedup por CPF dentro da própria importação.
     */
    private Paciente obterOuCriarPaciente(HorarioResolvido hr, Long unidadeId, Long uid,
            Map<String, Paciente> criadosPorCpf) {
        if (!hr.criarPaciente()) {
            return pacienteRepository.findById(hr.paciente().valor()).orElseThrow(() -> sumiu("Paciente"));
        }
        String cpf = hr.cpf().valor();
        Paciente jaCriado = cpf == null ? null : criadosPorCpf.get(cpf);
        if (jaCriado != null) {
            return jaCriado;
        }
        Paciente p = new Paciente();
        p.setNome(hr.nome().valor());
        String nomeSocial = hr.nomeSocial().valor();
        if (nomeSocial != null && !nomeSocial.isBlank()) {
            p.setNomeSocial(nomeSocial);
            p.setUtilizaNomeSocial(true);
        }
        p.setCpf(cpf);
        p.setDataNascimento(hr.dataNascimento().valor());
        if (hr.prontuarioNovo() != null && !hr.prontuarioNovo().isBlank()) {
            p.setProntuario(hr.prontuarioNovo());
        }
        if (hr.codigoNovo() != null && !hr.codigoNovo().isBlank()) {
            p.setCodigoIntegracao(hr.codigoNovo());
        }
        String telefone = hr.telefone().valor();
        if (telefone != null && !telefone.isBlank()) {
            p.getTelefonesAdicionais().add(telefone);
        }
        p.setAtivo(false); // ativação do app (código de acesso) é feita depois, manualmente — como no SIRESP
        unidadeRepository.findById(unidadeId).ifPresent(u -> p.getUnidades().add(u)); // vínculo já no snapshot do log
        Paciente salvo = pacienteRepository.save(p);
        pacienteLogService.registrarCriacaoImportacaoAgenda(salvo, uid);
        if (cpf != null) {
            criadosPorCpf.put(cpf, salvo);
        }
        return salvo;
    }

    /**
     * Notifica cada paciente do novo agendamento (push + inbox), FORA de transação e best-effort: uma falha de
     * notificação não desfaz a importação já gravada. Recarrega cada horário (associações EAGER) para o envio.
     */
    public void notificarImportados(List<Long> horarioIds) {
        for (Long id : horarioIds) {
            try {
                horarioRepository.findById(id).ifPresent(entregaService::notificarNovoAgendamento);
            } catch (RuntimeException ignorado) {
                // best-effort: não derruba o restante nem a importação já persistida
            }
        }
    }

    // ---------------- Resolução ----------------

    private AgendaResolvida resolver(byte[] bytes, Long unidadeId) {
        AgendaPlanilhaParser.Bruta bruta = parser.parse(bytes);
        Map<String, String> a = bruta.agenda();

        List<RefCadastro> profissionais = profissionalRepository.findAll().stream()
                .map(p -> new RefCadastro(p.getId(), p.getNome(), p.getCodigoIntegracao())).toList();
        List<RefCadastro> especialidades = especialidadeRepository.findAll().stream()
                .map(e -> new RefCadastro(e.getId(), e.getNome(), e.getCodigoIntegracao())).toList();
        List<RefCadastro> configuracoes = configuracaoAgendaRepository.findAll().stream()
                .map(c -> new RefCadastro(c.getId(), c.getNome(), null)).toList();

        Campo<LocalDate> data = resolverData(a.get("data"));
        Campo<Long> prof = resolverIdOuCodigo(a.get("profissionalId"), a.get("profissionalCodigo"), "Profissional", profissionais);
        Campo<Long> esp = resolverIdOuCodigo(a.get("especialidadeId"), a.get("especialidadeCodigo"), "Especialidade", especialidades);
        Campo<Long> cfg = resolverSomentePorId(a.get("configId"), "Configuração da Agenda", configuracoes);
        Campo<Long> uni = resolverUnidadeDoUsuario(unidadeId);
        CampoPreview nome = resolverNomeAgenda(a.getOrDefault("nome", ""));
        // Anti-duplicação: se já existe uma agenda com a MESMA Data + Profissional + Especialidade + Nome na mesma
        // unidade, a importação NÃO cria outra (erro bloqueante no preview). Só confere quando a chave está íntegra.
        if (!data.campo().erro() && !prof.campo().erro() && !esp.campo().erro() && !uni.campo().erro() && !nome.erro()
                && agendaRepository.existeAgendaMesmaIdentidade(uni.valor(), data.valor(), prof.valor(), esp.valor(),
                        nome.valor())) {
            nome = CampoPreview.erro(nome.valor(), "Já existe uma agenda com esta Data + Profissional + Especialidade "
                    + "+ Nome nesta unidade. A importação não cria agenda duplicada.");
        }

        List<HorarioResolvido> horarios = new ArrayList<>();
        for (AgendaPlanilhaParser.LinhaBruta l : bruta.horarios()) {
            // Dados do paciente da linha PRIMEIRO: a criação de um paciente novo exige todos preenchidos/válidos.
            Campo<String> cpf = resolverCpf(l.cpf());
            // 120 = limite de Paciente.nome (cadastro) — menor que Horario.importacao_nome (160) e é onde o valor
            // pode ir parar ao criar um paciente novo; validar pelo menor evita estourar o INSERT na gravação.
            Campo<String> nomePaciente = resolverTextoObrigatorio(l.nome(), "Nome", 120);
            Campo<String> nomeSocial = resolverNomeSocial(l.nomeSocial());
            Campo<String> telefone = resolverTelefone(l.telefone());
            Campo<LocalDate> dataNasc = resolverDataNascimento(l.dataNascimento());
            boolean demografiaOk = !cpf.campo().erro() && !nomePaciente.campo().erro()
                    && !telefone.campo().erro() && !dataNasc.campo().erro();

            PacienteResolvido pr = resolverPaciente(l.pacienteId(), l.pacienteProntuario(), l.pacienteCodigo(),
                    cpf.valor(), demografiaOk);
            Campo<Long> pac = pr.campo();
            // Regra 2: só quando o paciente foi LOCALIZADO pelo identificador, o CPF da linha tem que bater com o
            // do cadastro (quando achado pelo CPF, bate por definição; ao criar, não há cadastro para conferir).
            if (pr.conferirCpf() && !cpf.campo().erro() && cpf.valor() != null) {
                cpf = conferirCpfComCadastro(l.cpf(), cpf, pr.cpfCadastro());
            }

            Campo<LocalTime> inicio = resolverHora(l.inicio(), "Hora início", true);
            horarios.add(new HorarioResolvido(l.linhaExcel(), pac, pr.criar(), pr.prontuarioNovo(), pr.codigoNovo(),
                    cpf, nomePaciente, nomeSocial, telefone, dataNasc, inicio));
        }
        horarios = sinalizarIdentificadorNovoDuplicado(horarios);
        return new AgendaResolvida(data, prof, esp, cfg, uni, nome, horarios);
    }

    /**
     * Entre as linhas que vão CRIAR paciente: o MESMO prontuário/código novo com CPFs diferentes geraria dois
     * pacientes com o mesmo identificador (viola o índice único e abortaria a importação inteira). Converte essas
     * linhas em erro no preview (antes de gravar), em vez de deixar estourar o INSERT. Mesmo prontuário/código com
     * o MESMO CPF é a mesma pessoa em duas marcações — tratado na gravação (dedup por CPF), não é conflito.
     */
    private List<HorarioResolvido> sinalizarIdentificadorNovoDuplicado(List<HorarioResolvido> horarios) {
        List<ChaveCriacao> chaves = horarios.stream()
                .map(h -> new ChaveCriacao(h.criarPaciente(), h.prontuarioNovo(), h.codigoNovo(), h.cpf().valor()))
                .toList();
        java.util.Set<Integer> conflitos = indicesComIdentificadorNovoDuplicado(chaves);
        if (conflitos.isEmpty()) {
            return horarios;
        }
        List<HorarioResolvido> ajustado = new ArrayList<>(horarios.size());
        for (int i = 0; i < horarios.size(); i++) {
            HorarioResolvido h = horarios.get(i);
            if (!conflitos.contains(i)) {
                ajustado.add(h);
                continue;
            }
            boolean porProntuario = h.prontuarioNovo() != null;
            String campo = porProntuario ? "prontuário" : "código";
            String ident = (porProntuario ? "prontuário: " + h.prontuarioNovo() : "código: " + h.codigoNovo());
            Campo<Long> erroPac = erro(ident, "Mesmo " + campo + " em linhas com CPFs diferentes — um " + campo
                    + " novo não pode ser de dois pacientes.");
            ajustado.add(new HorarioResolvido(h.linha(), erroPac, false, null, null, h.cpf(), h.nome(),
                    h.nomeSocial(), h.telefone(), h.dataNascimento(), h.inicio()));
        }
        return ajustado;
    }

    /** Chaves de criação de uma linha (para a checagem pura de duplicidade de identificador novo). */
    record ChaveCriacao(boolean criar, String prontuario, String codigo, String cpf) {
    }

    /**
     * PURA (testável): índices das linhas que vão CRIAR e cujo prontuário/código novo aparece em outra linha de
     * criação com CPF diferente — conflito de identidade que precisa bloquear o preview.
     */
    static java.util.Set<Integer> indicesComIdentificadorNovoDuplicado(List<ChaveCriacao> chaves) {
        Map<String, java.util.Set<String>> cpfsPorProntuario = new HashMap<>();
        Map<String, java.util.Set<String>> cpfsPorCodigo = new HashMap<>();
        for (ChaveCriacao c : chaves) {
            if (!c.criar()) {
                continue;
            }
            if (c.prontuario() != null) {
                cpfsPorProntuario.computeIfAbsent(c.prontuario(), k -> new java.util.HashSet<>()).add(c.cpf());
            }
            if (c.codigo() != null) {
                cpfsPorCodigo.computeIfAbsent(c.codigo(), k -> new java.util.HashSet<>()).add(c.cpf());
            }
        }
        java.util.Set<Integer> conflitos = new java.util.HashSet<>();
        for (int i = 0; i < chaves.size(); i++) {
            ChaveCriacao c = chaves.get(i);
            if (!c.criar()) {
                continue;
            }
            boolean prontDup = c.prontuario() != null && cpfsPorProntuario.get(c.prontuario()).size() > 1;
            boolean codDup = c.codigo() != null && cpfsPorCodigo.get(c.codigo()).size() > 1;
            if (prontDup || codDup) {
                conflitos.add(i);
            }
        }
        return conflitos;
    }

    /** Profissional/Especialidade: exatamente UM entre id interno e código de integração. */
    private Campo<Long> resolverIdOuCodigo(String idVal, String codigoVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        String cod = trim(codigoVal);
        boolean temId = !id.isEmpty();
        boolean temCod = !cod.isEmpty();
        if (!temId && !temCod) {
            return erro("", rotulo + ": informe o id interno OU o código de integração.");
        }
        if (temId && temCod) {
            return erro("id: " + id + " · código: " + cod, rotulo + ": preencha apenas um (id interno OU código).");
        }
        if (temCod) {
            for (RefCadastro r : refs) {
                if (r.codigo() != null && !r.codigo().isBlank() && r.codigo().trim().equalsIgnoreCase(cod)) {
                    return new Campo<>(CampoPreview.ok("código: " + cod, r.nome()), r.id());
                }
            }
            return erro("código: " + cod, rotulo + ": código de integração não encontrado.");
        }
        return porId(id, rotulo, refs);
    }

    /** Configuração da Agenda: só id interno (não há código de integração na entidade). */
    private Campo<Long> resolverSomentePorId(String idVal, String rotulo, List<RefCadastro> refs) {
        String id = trim(idVal);
        if (id.isEmpty()) {
            return erro("", rotulo + ": informe o id interno.");
        }
        return porId(id, rotulo, refs);
    }

    private Campo<Long> porId(String id, String rotulo, List<RefCadastro> refs) {
        Long idn = parseLong(id);
        if (idn == null) {
            return erro("id: " + id, rotulo + ": id interno inválido.");
        }
        for (RefCadastro r : refs) {
            if (idn.equals(r.id())) {
                return new Campo<>(CampoPreview.ok("id: " + id, r.nome()), r.id());
            }
        }
        return erro("id: " + id, rotulo + ": id interno não encontrado.");
    }

    /** Unidade executante = a do usuário logado (não vem da planilha). */
    private Campo<Long> resolverUnidadeDoUsuario(Long unidadeId) {
        if (unidadeId == null) {
            return erro("", "Unidade do usuário logado não identificada.");
        }
        return unidadeRepository.findById(unidadeId)
                .map(u -> new Campo<>(CampoPreview.ok(u.getNome(), "unidade do usuário logado"), u.getId()))
                .orElseGet(() -> erro(String.valueOf(unidadeId), "Unidade do usuário logado não encontrada."));
    }

    /**
     * Resultado da localização do paciente da linha. {@code campo} = preview + id para gravar (id existente quando
     * localizado/vinculado; {@code null} quando vai criar ou quando há erro). {@code cpfCadastro} = CPF do cadastro
     * (só quando localizado pelo IDENTIFICADOR, para a conferência da Regra 2). {@code conferirCpf} liga a Regra 2.
     * {@code criar} = cadastrar um paciente NOVO na gravação, com {@code prontuarioNovo}/{@code codigoNovo} + os
     * dados da linha.
     */
    private record PacienteResolvido(Campo<Long> campo, String cpfCadastro, boolean conferirCpf, boolean criar,
            String prontuarioNovo, String codigoNovo) {
    }

    /** Tipo do único identificador preenchido na linha. */
    enum TipoIdentificador {
        ID_INTERNO, PRONTUARIO, CODIGO
    }

    /** Ação decidida para o paciente da linha (depois de olhar o banco). */
    enum AcaoPaciente {
        EXISTENTE_POR_IDENTIFICADOR, VINCULAR_POR_CPF, CRIAR_NOVO, ERRO_ID_NAO_ENCONTRADO, ERRO_NAO_ENCONTRADO_SEM_DADOS
    }

    /**
     * Matriz de decisão PURA (testável) da nova regra de cadastro automático. Precedência: achou pelo identificador
     * → EXISTENTE (aplica Regra 2 do CPF). Senão, por id interno → ERRO (não se cria paciente com id escolhido).
     * Senão (prontuário/código não achado): achou pelo CPF → VINCULAR_POR_CPF (não duplica a identidade); senão, com
     * CPF+Nome+Telefone+Data de nascimento válidos → CRIAR_NOVO; senão, erro pedindo os dados obrigatórios.
     */
    static AcaoPaciente decidirAcao(TipoIdentificador tipo, boolean achouPorIdentificador, boolean achouPorCpf,
            boolean demografiaOk) {
        if (achouPorIdentificador) {
            return AcaoPaciente.EXISTENTE_POR_IDENTIFICADOR;
        }
        if (tipo == TipoIdentificador.ID_INTERNO) {
            return AcaoPaciente.ERRO_ID_NAO_ENCONTRADO;
        }
        if (achouPorCpf) {
            return AcaoPaciente.VINCULAR_POR_CPF;
        }
        return demografiaOk ? AcaoPaciente.CRIAR_NOVO : AcaoPaciente.ERRO_NAO_ENCONTRADO_SEM_DADOS;
    }

    /**
     * Localiza o paciente (exatamente UM identificador) e decide: existente (por identificador), vincular a um
     * existente achado pelo CPF, criar um novo, ou erro. {@code cpfDigitos} = CPF já validado da linha (null se
     * inválido); {@code demografiaOk} = CPF+Nome+Telefone+Data de nascimento todos válidos (exigido p/ criar).
     */
    private PacienteResolvido resolverPaciente(String idVal, String prontVal, String codVal, String cpfDigitos,
            boolean demografiaOk) {
        String id = trim(idVal);
        String pront = trim(prontVal);
        String cod = trim(codVal);
        int preenchidos = (id.isEmpty() ? 0 : 1) + (pront.isEmpty() ? 0 : 1) + (cod.isEmpty() ? 0 : 1);
        if (preenchidos == 0) {
            return pacErro("", "Paciente: informe id interno, prontuário OU código de integração.");
        }
        if (preenchidos > 1) {
            List<String> partes = new ArrayList<>();
            if (!id.isEmpty()) {
                partes.add("id: " + id);
            }
            if (!pront.isEmpty()) {
                partes.add("prontuário: " + pront);
            }
            if (!cod.isEmpty()) {
                partes.add("código: " + cod);
            }
            return pacErro(String.join(" · ", partes), "Paciente: preencha apenas um identificador.");
        }

        TipoIdentificador tipo;
        String rotulo;
        java.util.Optional<Paciente> achado;
        if (!cod.isEmpty()) {
            // Limite de Paciente.codigo_integracao (60): um valor maior não casa com ninguém (os guardados são ≤60)
            // e estouraria o INSERT ao criar — acusa no preview em vez de abortar a gravação.
            if (cod.length() > 60) {
                return pacErro("código: " + cod, "Paciente: código de integração deve ter no máximo 60 caracteres.");
            }
            tipo = TipoIdentificador.CODIGO;
            rotulo = "código: " + cod;
            achado = pacienteRepository.findByCodigoIntegracao(cod);
        } else if (!pront.isEmpty()) {
            if (pront.length() > 60) {
                return pacErro("prontuário: " + pront, "Paciente: prontuário deve ter no máximo 60 caracteres.");
            }
            tipo = TipoIdentificador.PRONTUARIO;
            rotulo = "prontuário: " + pront;
            achado = pacienteRepository.findByProntuario(pront);
        } else {
            Long idn = parseLong(id);
            if (idn == null) {
                return pacErro("id: " + id, "Paciente: id interno inválido.");
            }
            tipo = TipoIdentificador.ID_INTERNO;
            rotulo = "id: " + id;
            achado = pacienteRepository.findById(idn);
        }

        boolean achouPorIdentificador = achado.isPresent();
        // Fallback por CPF só quando faz sentido: prontuário/código não achado e CPF da linha válido (evita duplicar
        // a identidade — o CPF é a chave do login). Não busca por CPF quando achou pelo identificador.
        Paciente porCpf = null;
        if (!achouPorIdentificador && tipo != TipoIdentificador.ID_INTERNO
                && cpfDigitos != null && !cpfDigitos.isBlank()) {
            porCpf = pacienteRepository.findByCpf(cpfDigitos).orElse(null);
        }

        return switch (decidirAcao(tipo, achouPorIdentificador, porCpf != null, demografiaOk)) {
            case EXISTENTE_POR_IDENTIFICADOR -> {
                Paciente p = achado.orElseThrow();
                yield new PacienteResolvido(new Campo<>(CampoPreview.ok(rotulo, p.nomeExibicao()), p.getId()),
                        p.getCpf(), true, false, null, null);
            }
            case VINCULAR_POR_CPF -> new PacienteResolvido(new Campo<>(
                    CampoPreview.ok("CPF: " + formatarCpf(cpfDigitos), porCpf.nomeExibicao() + " — localizado pelo CPF"),
                    porCpf.getId()), null, false, false, null, null);
            case CRIAR_NOVO -> new PacienteResolvido(
                    new Campo<>(CampoPreview.ok(rotulo, "Novo paciente — será cadastrado"), null), null, false, true,
                    tipo == TipoIdentificador.PRONTUARIO ? pront : null,
                    tipo == TipoIdentificador.CODIGO ? cod : null);
            case ERRO_ID_NAO_ENCONTRADO -> pacErro(rotulo, "Paciente: id interno não encontrado.");
            case ERRO_NAO_ENCONTRADO_SEM_DADOS -> pacErro(rotulo,
                    "Paciente não encontrado. Para cadastrar um novo, preencha CPF, Nome, Telefone e Data de "
                            + "nascimento (corrija os campos destacados).");
        };
    }

    private static PacienteResolvido pacErro(String valor, String mensagem) {
        return new PacienteResolvido(erro(valor, mensagem), null, false, false, null, null);
    }

    // ---- Dados do paciente informados na LINHA (gravados junto ao horário; não mexem no cadastro) ----

    /** CPF (obrigatório): valida os dígitos verificadores; grava só os 11 dígitos. */
    private Campo<String> resolverCpf(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", "CPF é obrigatório.");
        }
        if (!Documentos.cpfValido(v)) {
            return erro(valor, "CPF inválido.");
        }
        String digitos = Documentos.somenteDigitos(v);
        return new Campo<>(CampoPreview.ok(valor, formatarCpf(digitos)), digitos);
    }

    /** Desfecho da conferência entre o CPF da linha e o CPF do cadastro do paciente localizado. */
    enum ConferenciaCpf {
        OK, SEM_CPF_NO_CADASTRO, DIVERGE
    }

    /**
     * Regra (pura, testável): compara o CPF (só dígitos) informado na linha com o CPF do cadastro do paciente
     * localizado pelo identificador. {@code OK} = batem; {@code SEM_CPF_NO_CADASTRO} = o paciente não tem CPF no
     * cadastro (não dá para conferir); {@code DIVERGE} = CPFs diferentes.
     */
    static ConferenciaCpf conferirCpf(String cpfLinhaDigitos, String cpfCadastro) {
        String doCadastro = Documentos.somenteDigitos(cpfCadastro);
        if (doCadastro == null) {
            return ConferenciaCpf.SEM_CPF_NO_CADASTRO;
        }
        return doCadastro.equals(Documentos.somenteDigitos(cpfLinhaDigitos))
                ? ConferenciaCpf.OK
                : ConferenciaCpf.DIVERGE;
    }

    /**
     * Aplica a regra "o CPF da linha tem que bater com o do cadastro" quando o paciente foi localizado por
     * identificador. Erro (bloqueia o "Confirmar") quando diverge ou quando o cadastro não tem CPF para conferir.
     */
    private Campo<String> conferirCpfComCadastro(String valorDigitado, Campo<String> cpf, String cpfCadastro) {
        return switch (conferirCpf(cpf.valor(), cpfCadastro)) {
            case OK -> cpf;
            case SEM_CPF_NO_CADASTRO -> erro(valorDigitado,
                    "O paciente localizado não tem CPF no cadastro para conferir.");
            case DIVERGE -> erro(valorDigitado,
                    "CPF não confere com o cadastro do paciente (cadastro: " + mascararCpf(Documentos.somenteDigitos(cpfCadastro)) + ").");
        };
    }

    /**
     * Campo de texto obrigatório (ex.: Nome): não pode ficar em branco e tem que caber no tamanho máximo —
     * {@code max} deve ser o MENOR limite entre os destinos (o cadastro do paciente, quando a linha pode criar
     * um paciente novo, costuma ser mais curto que a coluna do horário). NÃO trunca em silêncio: acusa erro para
     * o operador corrigir (truncar um nome calado seria pior, e o valor pode ir parar no cadastro do paciente).
     */
    private Campo<String> resolverTextoObrigatorio(String valor, String rotulo, int max) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", rotulo + " é obrigatório.");
        }
        if (v.length() > max) {
            return erro(valor, rotulo + ": no máximo " + max + " caracteres.");
        }
        return new Campo<>(CampoPreview.ok(valor), v);
    }

    /** Nome social (opcional): no máximo 120 (limite do cadastro); vazio → null (preenchido ⇒ "utiliza nome social"). */
    private Campo<String> resolverNomeSocial(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return new Campo<>(CampoPreview.ok(""), null);
        }
        if (v.length() > 120) {
            return erro(valor, "Nome social: no máximo 120 caracteres.");
        }
        return new Campo<>(CampoPreview.ok(valor), v);
    }

    /** Telefone (obrigatório): exige DDD + número (>= 10 dígitos); grava só os dígitos. */
    private Campo<String> resolverTelefone(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", "Telefone é obrigatório.");
        }
        String digitos = v.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 13) {
            return erro(valor, "Telefone inválido (informe DDD + número).");
        }
        return new Campo<>(CampoPreview.ok(valor), digitos);
    }

    /** Data de nascimento (obrigatória): data válida (dd/mm/aaaa), não futura. */
    private Campo<LocalDate> resolverDataNascimento(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", "Data de nascimento é obrigatória.");
        }
        LocalDate data = parseData(v);
        if (data == null || data.getYear() <= 1) {
            return erro(valor, "Data de nascimento inválida (use dd/mm/aaaa).");
        }
        if (data.isAfter(LocalDate.now())) {
            return erro(valor, "Data de nascimento não pode ser futura.");
        }
        return new Campo<>(CampoPreview.ok(valor, data.format(DATA_SAIDA)), data);
    }

    /** 11 dígitos → 000.000.000-00 (apresentação no preview). */
    private static String formatarCpf(String d) {
        if (d == null || d.length() != 11) {
            return d;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    /**
     * CPF do cadastro MASCARADO para a mensagem de divergência (LGPD — minimização): mostra só os 2 últimos
     * dígitos (***.***.***-NN), o suficiente para o operador perceber que apontou o paciente errado sem expor
     * o CPF real do cadastro na tela de preview.
     */
    private static String mascararCpf(String d) {
        if (d == null || d.length() != 11) {
            return "***";
        }
        return "***.***.***-" + d.substring(9);
    }

    private Campo<LocalDate> resolverData(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return erro("", "Data é obrigatória.");
        }
        LocalDate data = parseData(v);
        return data == null
                ? erro(valor, "Data inválida (use dd/mm/aaaa).")
                : new Campo<>(CampoPreview.ok(valor, data.format(DATA_SAIDA)), data);
    }

    /**
     * Nome da agenda (OBRIGATÓRIO — faz parte da chave anti-duplicação Data+Profissional+Especialidade+Nome): não
     * pode ficar em branco e no máximo 255 (= Agenda.nome). Acusar erro no preview evita tanto a agenda sem rótulo
     * quanto o valor longo estourar o INSERT e abortar a importação.
     */
    private CampoPreview resolverNomeAgenda(String valor) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return CampoPreview.erro("", "Nome da agenda é obrigatório.");
        }
        if (v.length() > 255) {
            return CampoPreview.erro(valor, "Nome da agenda: no máximo 255 caracteres.");
        }
        return CampoPreview.ok(valor);
    }

    private Campo<LocalTime> resolverHora(String valor, String rotulo, boolean obrigatorio) {
        String v = trim(valor);
        if (v.isEmpty()) {
            return obrigatorio ? erro("", rotulo + " é obrigatória.") : new Campo<>(CampoPreview.ok(""), null);
        }
        LocalTime hora = parseHora(v);
        return hora == null
                ? erro(valor, rotulo + " inválida (use HH:mm).")
                : new Campo<>(CampoPreview.ok(valor, hora.format(HORA_ACEITAS.get(0))), hora);
    }

    // ---------------- Parse / helpers ----------------

    private static LocalDate parseData(String v) {
        for (DateTimeFormatter f : DATA_ACEITAS) {
            try {
                return LocalDate.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalTime parseHora(String v) {
        for (DateTimeFormatter f : HORA_ACEITAS) {
            try {
                return LocalTime.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static Long parseLong(String v) {
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /** Erro de resolução: campo destacado + sem valor para gravar. */
    private static <T> Campo<T> erro(String valor, String mensagem) {
        return new Campo<>(CampoPreview.erro(valor, mensagem), null);
    }

    private static ResponseStatusException sumiu(String rotulo) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                rotulo + " não está mais disponível. Reimporte para revalidar.");
    }

    private static int totalErros(AgendaResolvida r) {
        int n = conta(r.data().campo(), r.profissional().campo(), r.especialidade().campo(),
                r.config().campo(), r.unidade().campo(), r.nome());
        for (HorarioResolvido h : r.horarios()) {
            n += conta(h.paciente().campo(), h.cpf().campo(), h.nome().campo(), h.nomeSocial().campo(),
                    h.telefone().campo(), h.dataNascimento().campo(), h.inicio().campo());
        }
        return n;
    }

    private static int conta(CampoPreview... campos) {
        int n = 0;
        for (CampoPreview c : campos) {
            if (c != null && c.erro()) {
                n++;
            }
        }
        return n;
    }
}
