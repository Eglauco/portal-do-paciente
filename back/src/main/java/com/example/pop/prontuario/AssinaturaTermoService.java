package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Horario;
import com.example.pop.assinatura.AssinaturaProvider;
import com.example.pop.assinatura.AssinaturaProviderFactory;
import com.example.pop.assinatura.ProvedorAssinatura;
import com.example.pop.paciente.Paciente;
import com.example.pop.configuracaoagenda.OrigemModeloTermo;
import com.example.pop.configuracaoagenda.TermoConfiguracaoAgenda;
import com.example.pop.configuracaoagenda.TermoConfiguracaoAgendaRepository;
import com.example.pop.configuracaoagenda.TermoVariavelResolver;
import com.example.pop.storage.StorageService;

/**
 * Assinatura eletrônica de termos (TCLE) — INDEPENDENTE de provedor. {@code iniciar}/{@code iniciarLote}
 * criam os documentos no provedor ATIVO (ZapSign/Autentique) com as variáveis resolvidas e devolvem os
 * links da cerimônia. {@code processarAssinados}/{@code processarRecusa} são chamados pelos webhooks:
 * baixam o PDF assinado, guardam no S3, criam o Documento no prontuário e marcam ASSINADO (ou liberam para
 * refazer, na recusa). O provedor é escondido atrás de {@link AssinaturaProvider}. Só {@code .docx} é assinável.
 */
@Service
public class AssinaturaTermoService {

    /** Status em que um termo ainda pode ser (re)assinado. */
    private static final List<StatusTermoAssinatura> ASSINAVEIS =
            List.of(StatusTermoAssinatura.PENDENTE, StatusTermoAssinatura.TENTAR_NOVAMENTE);

    private final TermoAssinaturaRepository termoRepository;
    private final TermoConfiguracaoAgendaRepository termoConfiguracaoAgendaRepository;
    private final DocumentoRepository documentoRepository;
    private final StorageService storageService;
    private final TermoVariavelResolver variavelResolver;
    private final AssinaturaProviderFactory providerFactory;

    public AssinaturaTermoService(TermoAssinaturaRepository termoRepository,
            TermoConfiguracaoAgendaRepository termoConfiguracaoAgendaRepository, DocumentoRepository documentoRepository,
            StorageService storageService, TermoVariavelResolver variavelResolver,
            AssinaturaProviderFactory providerFactory) {
        this.termoRepository = termoRepository;
        this.termoConfiguracaoAgendaRepository = termoConfiguracaoAgendaRepository;
        this.documentoRepository = documentoRepository;
        this.storageService = storageService;
        this.variavelResolver = variavelResolver;
        this.providerFactory = providerFactory;
    }

    /** Links da cerimônia + provedor usado (o app decide a UX de conclusão conforme o provedor). */
    public record InicioAssinatura(List<String> signUrls, String provedor) {
    }

    /** Inicia a assinatura de um único termo do paciente logado e devolve o(s) link(s) da cerimônia. */
    @Transactional
    public InicioAssinatura iniciar(Long termoId, Long pacienteId) {
        TermoAssinatura termo = termoRepository.findByIdAndProntuario_Horario_Paciente_Id(termoId, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Termo não encontrado"));
        if (!ehAssinavel(termo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este termo não está mais pendente de assinatura.");
        }
        return criarSessoes(List.of(termo), termo.getProntuario().getHorario());
    }

    /**
     * Inicia a assinatura EM LOTE de todos os termos assináveis de um atendimento (prontuário) do paciente
     * logado. O provedor decide como agrupar (ZapSign: uma cerimônia com extras; Autentique: um PDF combinado
     * ou N documentos, conforme a config). Devolve os links (1 para a maioria; N no modo separado da Autentique).
     */
    @Transactional
    public InicioAssinatura iniciarLote(Long prontuarioId, Long pacienteId) {
        // Assináveis = pendentes de assinatura OU que falharam no provedor (recusa) e vão refazer.
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Horario_Paciente_IdAndStatusInOrderByCriadoEmAsc(
                        prontuarioId, pacienteId, ASSINAVEIS);
        if (termos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum termo pendente para assinar.");
        }
        return criarSessoes(termos, termos.get(0).getProntuario().getHorario());
    }

    /**
     * Cria os documentos no provedor ativo para os termos dados, guarda em cada termo o id do documento/
     * signatário + o provedor, e devolve os links de assinatura (distintos) para o app abrir.
     */
    private InicioAssinatura criarSessoes(List<TermoAssinatura> termos, Horario ag) {
        AssinaturaProvider provider = providerFactory.ativo();
        if (!provider.disponivel()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assinatura eletrônica indisponível (provedor não configurado).");
        }
        AssinaturaProvider.Signatario signatario = signatario(ag.getPaciente());
        Map<String, String> variaveis = variavelResolver.resolver(ag);

        List<AssinaturaProvider.TermoParaAssinar> entradas = new ArrayList<>();
        for (TermoAssinatura t : termos) {
            TermoConfiguracaoAgenda tp = t.getTermoConfiguracaoAgenda();
            String templateToken = garantirModelo(tp, provider);
            entradas.add(new AssinaturaProvider.TermoParaAssinar(
                    t.getId(), t.getNome(), tp == null ? null : tp.getUrl(),
                    tp == null ? null : tp.getContentType(), templateToken, variaveis));
        }

        List<AssinaturaProvider.DocumentoAssinatura> docs =
                provider.criar(entradas, signatario, providerFactory.combinarLote());

        Map<Long, TermoAssinatura> porId = new LinkedHashMap<>();
        for (TermoAssinatura t : termos) {
            porId.put(t.getId(), t);
        }
        List<String> urls = new ArrayList<>();
        for (AssinaturaProvider.DocumentoAssinatura d : docs) {
            for (Long tid : d.termoIds()) {
                TermoAssinatura t = porId.get(tid);
                if (t == null) {
                    continue;
                }
                t.setProviderDocToken(d.providerDocToken());
                if (d.providerSignerId() != null) {
                    t.setProviderSignerId(d.providerSignerId());
                }
                t.setProvedor(provider.id());
                termoRepository.save(t);
            }
            if (d.signUrl() != null && !d.signUrl().isBlank() && !urls.contains(d.signUrl())) {
                urls.add(d.signUrl());
            }
        }
        if (urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O provedor não devolveu um link de assinatura.");
        }

        // Coassinatura: adiciona o PROFISSIONAL como 2º signatário aos termos que exigem (após o paciente).
        // A ordem é garantida pela máquina de estados (o link do profissional só é exposto após o paciente
        // assinar). Bloqueia-e-avisa se o provedor não suporta ou se falta o CPF do profissional.
        List<TermoAssinatura> coassinar = termos.stream().filter(TermoAssinatura::isProfissionalAssina).toList();
        if (!coassinar.isEmpty()) {
            if (!provider.suportaCoassinatura()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "A coassinatura do profissional não é suportada pelo provedor " + provider.id().name()
                                + " (disponível em ZapSign, DocuSign e Autentique). Troque o provedor de assinatura "
                                + "ou desmarque a coassinatura no termo.");
            }
            // Se algum termo da cerimônia exige certificado (qualificada), o profissional assina qualificada (cobre todos).
            boolean usarCertificado = coassinar.stream().anyMatch(TermoAssinatura::isProfissionalCertificado);
            if (usarCertificado && !provider.suportaCoassinaturaCertificado()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "A coassinatura COM certificado digital (ICP) está disponível apenas no provedor ZapSign. "
                                + "Use o ZapSign ou desmarque 'assinar com certificado' no termo.");
            }
            AssinaturaProvider.Signatario sigProf = signatarioProfissional(ag.getProfissionalSaude());
            // O profissional é adicionado UMA VEZ, no documento PRINCIPAL da cerimônia (o que tem o
            // signatário do paciente). No lote da ZapSign os extras são cobertos pelo principal — adicionar
            // um signatário a um extra dá 400. Reaproveita o mesmo signUrl do profissional nos termos.
            String principalDocToken = termos.stream()
                    .filter(t -> t.getProviderSignerId() != null && t.getProviderDocToken() != null
                            && !t.getProviderDocToken().isBlank())
                    .map(TermoAssinatura::getProviderDocToken)
                    .findFirst()
                    .orElseGet(() -> coassinar.stream().map(TermoAssinatura::getProviderDocToken)
                            .filter(dt -> dt != null && !dt.isBlank()).findFirst().orElse(null));
            if (principalDocToken != null) {
                AssinaturaProvider.Coassinante c =
                        provider.adicionarCoassinante(principalDocToken, sigProf, usarCertificado);
                for (TermoAssinatura t : coassinar) {
                    t.setProviderSignerIdProfissional(c.providerSignerId());
                    t.setSignUrlProfissional(c.signUrl());
                    termoRepository.save(t);
                }
                // Alguns provedores (DocuSign) INVALIDAM a URL da cerimônia do paciente ao adicionar o 2º
                // signatário; regenera a URL do paciente DEPOIS da coassinatura (no-op nos demais provedores).
                if (!urls.isEmpty()) {
                    String regenerada = provider.regenerarUrlPaciente(principalDocToken, signatario, urls.get(0));
                    if (regenerada != null && !regenerada.isBlank()) {
                        urls.set(0, regenerada);
                    }
                }
            }
        }

        // Finaliza a criação APÓS a eventual coassinatura. No Clicksign isso ativa o envelope (draft -> running),
        // que foi adiado p/ permitir adicionar o profissional; nos demais provedores é no-op (já ativaram no criar).
        termos.stream()
                .map(TermoAssinatura::getProviderDocToken)
                .filter(dt -> dt != null && !dt.isBlank())
                .findFirst()
                .ifPresent(provider::finalizarCriacao);

        return new InicioAssinatura(urls, provider.id().name());
    }

    /** Signatário da coassinatura = o profissional do atendimento. Exige CPF (bloqueia-e-avisa se faltar). */
    private AssinaturaProvider.Signatario signatarioProfissional(com.example.pop.profissional.ProfissionalSaude prof) {
        if (prof == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Coassinatura: o atendimento não tem profissional de saúde definido.");
        }
        String cpf = digitos(prof.getCpf());
        if (cpf.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Coassinatura: complete o CPF do profissional " + prof.getNome()
                            + " no cadastro (ou desmarque a coassinatura no termo).");
        }
        return new AssinaturaProvider.Signatario(prof.getNome(), cpf, "55",
                digitos(prof.getTelefone()), prof.getEmail(), "profissional-" + prof.getId());
    }

    /**
     * Marca como EM_CONFIRMACAO os termos assináveis (com documento já criado) do atendimento — chamado pelo
     * app ao concluir a cerimônia (feedback imediato: some o botão). Não toca em termos já ASSINADO (evita
     * corrida com o webhook). Devolve os termos do atendimento.
     */
    @Transactional
    public List<TermoAssinatura> marcarEmConfirmacao(Long prontuarioId, Long pacienteId) {
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Horario_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
        for (TermoAssinatura t : termos) {
            boolean iniciado = t.getProviderDocToken() != null && !t.getProviderDocToken().isBlank();
            if (iniciado && ehAssinavel(t.getStatus())) {
                t.setStatus(StatusTermoAssinatura.EM_CONFIRMACAO);
                termoRepository.save(t);
            }
        }
        return termos;
    }

    /**
     * Conferência do loop do app: verifica ATIVAMENTE no provedor se cada termo em confirmação já foi
     * REALMENTE assinado (o provedor só devolve o arquivo quando a assinatura está concluída) e, em caso
     * afirmativo, baixa/guarda e marca ASSINADO — sem depender do webhook. Depois relê e devolve os termos.
     */
    @Transactional
    public List<TermoAssinatura> conferir(Long prontuarioId, Long pacienteId) {
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Horario_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
        java.util.Set<String> processados = new java.util.HashSet<>();
        for (TermoAssinatura t : termos) {
            // Dedupe pelo documento: no COMBINADO vários termos compartilham o mesmo doc (consulta 1x).
            if (t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO
                    && t.getProviderDocToken() != null && !t.getProviderDocToken().isBlank()
                    && processados.add(t.getProviderDocToken())) {
                try {
                    AssinaturaProvider provider = providerFactory.porId(t.getProvedor());
                    for (AssinaturaProvider.ArquivoAssinado a : provider.coletarAssinados(t.getProviderDocToken())) {
                        guardarAssinado(a.providerDocToken(), a.pdf());
                    }
                } catch (RuntimeException ignored) {
                    // Falha transitória do provedor: tenta de novo no próximo tick do loop.
                }
            }
        }
        // Coassinatura: se o paciente já assinou a cerimônia mas ela não concluiu (falta o profissional),
        // os termos ainda EM_CONFIRMACAO viram AGUARDANDO_PROFISSIONAL (o profissional assina pelo painel).
        aplicarAguardandoProfissional(termos);
        return termoRepository
                .findByProntuario_IdAndProntuario_Horario_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
    }

    /**
     * Se a cerimônia exige o profissional e o PACIENTE já assinou (detectado no documento principal), move
     * TODOS os termos ainda EM_CONFIRMACAO do atendimento para AGUARDANDO_PROFISSIONAL. Nível de cerimônia:
     * no lote da ZapSign os extras compartilham a mesma cerimônia do principal.
     */
    private void aplicarAguardandoProfissional(List<TermoAssinatura> termos) {
        boolean coassinaEmConfirmacao = termos.stream()
                .anyMatch(t -> t.isProfissionalAssina() && t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO);
        if (!coassinaEmConfirmacao || !pacienteAssinouCerimonia(termos)) {
            return;
        }
        for (TermoAssinatura t : termos) {
            if (t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO) {
                t.setStatus(StatusTermoAssinatura.AGUARDANDO_PROFISSIONAL);
                termoRepository.save(t);
            }
        }
    }

    /** true se o PACIENTE já concluiu a assinatura (checado no documento principal, que tem o signer dele). */
    private boolean pacienteAssinouCerimonia(List<TermoAssinatura> termos) {
        for (TermoAssinatura t : termos) {
            if (t.getProviderSignerId() != null && t.getProviderDocToken() != null
                    && !t.getProviderDocToken().isBlank()) {
                try {
                    if (providerFactory.porId(t.getProvedor())
                            .signatarioConcluiu(t.getProviderDocToken(), t.getProviderSignerId())) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // provedor indisponível/sem suporte: tenta no próximo tick
                }
            }
        }
        return false;
    }

    // ---------- Coassinatura: fila e conferência do PROFISSIONAL (painel, cerimônia embutida) ----------

    /** Atendimentos com termos aguardando a coassinatura DESTE profissional (agrupados por cerimônia). */
    @Transactional(readOnly = true)
    public List<TermoProfissionalResponse> listarTermosProfissional(Long profissionalSaudeId) {
        List<TermoAssinatura> termos = termoRepository
                .findByStatusAndProntuario_Horario_Agenda_ProfissionalSaude_IdOrderByCriadoEmAsc(
                        StatusTermoAssinatura.AGUARDANDO_PROFISSIONAL, profissionalSaudeId);
        Map<Long, List<TermoAssinatura>> porProntuario = new LinkedHashMap<>();
        for (TermoAssinatura t : termos) {
            porProntuario.computeIfAbsent(t.getProntuario().getId(), k -> new ArrayList<>()).add(t);
        }
        List<TermoProfissionalResponse> out = new ArrayList<>();
        for (List<TermoAssinatura> lista : porProntuario.values()) {
            TermoAssinatura primeiro = lista.get(0);
            Horario ag = primeiro.getProntuario().getHorario();
            boolean certificado = lista.stream().anyMatch(TermoAssinatura::isProfissionalCertificado);
            // URL da cerimônia do profissional: durável (usa a guardada) OU efêmera (gera agora — ex.: DocuSign).
            String signUrl = urlCerimoniaProfissional(lista, ag);
            boolean abrirEmAba = false;
            try {
                abrirEmAba = !providerFactory.porId(primeiro.getProvedor()).cerimoniaCoassinaturaEmbutivel();
            } catch (RuntimeException ignored) {
                // provedor não registrado: mantém embutido (padrão)
            }
            out.add(new TermoProfissionalResponse(
                    primeiro.getProntuario().getId(),
                    ag.getPaciente() == null ? null : ag.getPaciente().getNome(),
                    ag.getEspecialidade() == null ? null : ag.getEspecialidade().getNome(),
                    ag.getDataHora(),
                    signUrl,
                    certificado,
                    abrirEmAba,
                    lista.stream().map(TermoAssinatura::getNome).toList()));
        }
        return out;
    }

    /**
     * URL da cerimônia do profissional para um atendimento: provedores de URL DURÁVEL devolvem a guardada; os de
     * URL EFÊMERA (DocuSign) geram uma nova sob demanda. Best-effort: se o provedor falhar (ex.: gerar recipient
     * view), cai na URL guardada de algum termo, ou null (o front desabilita o botão).
     */
    private String urlCerimoniaProfissional(List<TermoAssinatura> lista, Horario ag) {
        com.example.pop.profissional.ProfissionalSaude prof = ag == null ? null : ag.getProfissionalSaude();
        for (TermoAssinatura t : lista) {
            if (t.getProviderDocToken() == null || t.getProviderDocToken().isBlank()) {
                continue;
            }
            try {
                return providerFactory.porId(t.getProvedor()).urlCoassinante(
                        t.getProviderDocToken(), t.getProviderSignerIdProfissional(),
                        signatarioProfissionalLeniente(prof), t.getSignUrlProfissional());
            } catch (RuntimeException ignored) {
                // provedor indisponível/erro ao gerar URL efêmera: tenta o próximo termo (ou cai no fallback)
            }
        }
        return lista.stream().map(TermoAssinatura::getSignUrlProfissional)
                .filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
    }

    /** Signatário do profissional SEM validar CPF (para gerar a URL da cerimônia; a validação de CPF é no início). */
    private AssinaturaProvider.Signatario signatarioProfissionalLeniente(
            com.example.pop.profissional.ProfissionalSaude prof) {
        if (prof == null) {
            return null;
        }
        return new AssinaturaProvider.Signatario(prof.getNome(), digitos(prof.getCpf()), "55",
                digitos(prof.getTelefone()), prof.getEmail(), "profissional-" + prof.getId());
    }

    /**
     * Conferência do loop do profissional: verifica ativamente no provedor se o documento concluiu (paciente
     * + profissional) e, em caso afirmativo, baixa/guarda e marca ASSINADO. Posse pelo profissionalSaudeId.
     */
    @Transactional
    public List<TermoAssinatura> conferirProfissional(Long prontuarioId, Long profissionalSaudeId) {
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Horario_Agenda_ProfissionalSaude_IdAndStatusOrderByCriadoEmAsc(
                        prontuarioId, profissionalSaudeId, StatusTermoAssinatura.AGUARDANDO_PROFISSIONAL);
        java.util.Set<String> processados = new java.util.HashSet<>();
        for (TermoAssinatura t : termos) {
            if (t.getProviderDocToken() != null && !t.getProviderDocToken().isBlank()
                    && processados.add(t.getProviderDocToken())) {
                try {
                    AssinaturaProvider provider = providerFactory.porId(t.getProvedor());
                    for (AssinaturaProvider.ArquivoAssinado a : provider.coletarAssinados(t.getProviderDocToken())) {
                        guardarAssinado(a.providerDocToken(), a.pdf());
                    }
                } catch (RuntimeException ignored) {
                    // falha transitória do provedor: tenta de novo no próximo tick
                }
            }
        }
        return termoRepository.findByProntuario_IdAndProntuario_Horario_Agenda_ProfissionalSaude_IdOrderByCriadoEmAsc(
                prontuarioId, profissionalSaudeId);
    }

    /**
     * Cancela a confirmação: volta os termos EM_CONFIRMACAO do atendimento para PENDENTE (limpando o
     * documento do provedor), para o paciente refazer — usado quando ele saiu da cerimônia sem assinar.
     * Não toca em termos já ASSINADO.
     */
    @Transactional
    public List<TermoAssinatura> cancelarConfirmacao(Long prontuarioId, Long pacienteId) {
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Horario_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
        for (TermoAssinatura t : termos) {
            if (t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO) {
                t.setStatus(StatusTermoAssinatura.PENDENTE);
                t.setProviderDocToken(null);
                t.setProviderSignerId(null);
                termoRepository.save(t);
            }
        }
        return termos;
    }

    /**
     * Webhook de assinatura: para cada documento afetado, o provedor devolve o(s) arquivo(s) assinado(s); aqui
     * guardamos no S3, criamos o Documento no prontuário e marcamos ASSINADO. Idempotente.
     */
    @Transactional
    public void processarAssinados(AssinaturaProvider provider, List<String> webhookDocTokens) {
        if (webhookDocTokens == null) {
            return;
        }
        for (String token : webhookDocTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            List<AssinaturaProvider.ArquivoAssinado> assinados = provider.coletarAssinados(token);
            if (!assinados.isEmpty()) {
                for (AssinaturaProvider.ArquivoAssinado a : assinados) {
                    guardarAssinado(a.providerDocToken(), a.pdf());
                }
            } else {
                // Documento ainda não concluído: pode ser a assinatura PARCIAL do paciente numa cerimônia
                // com coassinatura → move o atendimento para AGUARDANDO_PROFISSIONAL (profissional assina no painel).
                List<TermoAssinatura> refs = termoRepository.findAllByProviderDocToken(token);
                if (!refs.isEmpty()) {
                    aplicarAguardandoProfissional(
                            termoRepository.findByProntuario_IdOrderByCriadoEmAsc(refs.get(0).getProntuario().getId()));
                }
            }
        }
    }

    /**
     * Guarda UM documento assinado — que pode cobrir VÁRIOS termos (modo COMBINADO, em que os termos
     * compartilham o mesmo documento do provedor): salva o PDF uma vez, cria um Documento no prontuário e
     * marca TODOS os termos daquele documento como ASSINADO. Idempotente (ignora os já ASSINADO).
     */
    private void guardarAssinado(String providerDocToken, byte[] pdf) {
        if (providerDocToken == null || providerDocToken.isBlank() || pdf == null || pdf.length == 0) {
            return;
        }
        List<TermoAssinatura> termos = termoRepository.findAllByProviderDocToken(providerDocToken).stream()
                .filter(t -> t.getStatus() != StatusTermoAssinatura.ASSINADO)
                .toList();
        if (termos.isEmpty()) {
            return; // token desconhecido ou já processado
        }
        boolean combinado = termos.size() > 1;
        String nomeBase = combinado ? "Termos de Consentimento" : termos.get(0).getNome();
        String url = storageService.salvarBytes(pdf, "application/pdf", "tcle-assinado", nomeBase + ".pdf");

        Documento documento = new Documento();
        documento.setProntuario(termos.get(0).getProntuario());
        documento.setNome(nomeBase + " (assinado)");
        documento.setUrl(url);
        documento.setStatusAnalise(StatusAnaliseDocumento.NAO_ANALISAVEL); // termo assinado não passa pela IA
        documentoRepository.save(documento);

        LocalDateTime agora = LocalDateTime.now();
        for (TermoAssinatura t : termos) {
            t.setStatus(StatusTermoAssinatura.ASSINADO);
            t.setSignedUrl(url);
            t.setAssinadoEm(agora);
            if (t.isProfissionalAssina() && t.getAssinadoEmProfissional() == null) {
                t.setAssinadoEmProfissional(agora); // o doc só concluiu porque o profissional coassinou
            }
            termoRepository.save(t);
        }
    }

    /**
     * Webhook de recusa: a assinatura foi recusada no provedor. Joga os termos em confirmação/pendentes do
     * MESMO atendimento para TENTAR_NOVAMENTE (o app oferece refazer a cerimônia). Não mexe em ASSINADO.
     */
    @Transactional
    public void processarRecusa(List<String> providerDocTokens) {
        if (providerDocTokens == null) {
            return;
        }
        for (String token : providerDocTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            List<TermoAssinatura> refs = termoRepository.findAllByProviderDocToken(token);
            if (refs.isEmpty()) {
                continue;
            }
            List<TermoAssinatura> irmaos = termoRepository
                    .findByProntuario_IdOrderByCriadoEmAsc(refs.get(0).getProntuario().getId());
            for (TermoAssinatura t : irmaos) {
                if (t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO
                        || t.getStatus() == StatusTermoAssinatura.PENDENTE) {
                    t.setStatus(StatusTermoAssinatura.TENTAR_NOVAMENTE);
                    termoRepository.save(t);
                }
            }
        }
    }

    private static boolean ehAssinavel(StatusTermoAssinatura status) {
        return ASSINAVEIS.contains(status);
    }

    /** Signatário da cerimônia = o paciente. */
    private AssinaturaProvider.Signatario signatario(Paciente paciente) {
        // Paciente SEM e-mail (null) de propósito: na Autentique o paciente assina name-only (short_link no app);
        // passar e-mail dispararia e-mail e zeraria o link. O e-mail só é usado p/ pré-preencher o profissional.
        return new AssinaturaProvider.Signatario(
                paciente.getNome(), digitos(paciente.getCpf()), "55", digitos(primeiroTelefone(paciente)),
                null, "paciente-" + paciente.getId());
    }

    /**
     * Garante o modelo do termo para o provedor: exige {@code .docx}; para provedor com modelo remoto (ZapSign)
     * usa/registra o token; para provedor que renderiza local (Autentique) devolve null (usa o próprio .docx).
     */
    private String garantirModelo(TermoConfiguracaoAgenda tp, AssinaturaProvider provider) {
        if (tp == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Termo indisponível para assinatura (origem removida).");
        }
        // Termo amarrado a um MODELO do ZapSign: só o ZapSign assina (os outros não têm esse modelo).
        if (tp.getOrigemModelo() == OrigemModeloTermo.ZAPSIGN_MODELO) {
            if (provider.id() != ProvedorAssinatura.ZAPSIGN) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Este termo usa um modelo do ZapSign e só pode ser assinado com o provedor ZapSign. "
                                + "Ajuste o provedor de assinatura ou configure um arquivo .docx no termo.");
            }
            if (tp.getProviderTemplateToken() == null || tp.getProviderTemplateToken().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Modelo do ZapSign não configurado neste termo.");
            }
            return tp.getProviderTemplateToken();
        }
        if (!ehDocx(tp.getUrl(), tp.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Termo em formato .doc não pode ser assinado. Reenvie o arquivo em .docx.");
        }
        if (!provider.usaModeloRemoto()) {
            return null; // Autentique: renderiza o documento localmente a partir do .docx
        }
        if (tp.getProviderTemplateToken() != null && !tp.getProviderTemplateToken().isBlank()) {
            return tp.getProviderTemplateToken();
        }
        // .docx sem token remoto (upload antigo, troca de provedor ou falha no registro): registra agora.
        byte[] bytes = storageService.baixarBytes(tp.getUrl());
        if (bytes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível ler o arquivo do termo.");
        }
        String token = provider.registrarModelo(tp.getNome(), bytes);
        tp.setProviderTemplateToken(token);
        termoConfiguracaoAgendaRepository.save(tp);
        return token;
    }

    private static boolean ehDocx(String url, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("wordprocessingml")) {
            return true;
        }
        return url != null && url.toLowerCase().endsWith(".docx");
    }

    private static String primeiroTelefone(Paciente p) {
        List<String> tels = p.getTelefonesAdicionais();
        return tels == null || tels.isEmpty() ? null : tels.get(0);
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
