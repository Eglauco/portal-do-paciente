package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.paciente.Paciente;
import com.example.pop.procedimento.TermoProcedimento;
import com.example.pop.procedimento.TermoProcedimentoRepository;
import com.example.pop.procedimento.TermoVariavelResolver;
import com.example.pop.storage.StorageService;
import com.example.pop.zapsign.ZapSignClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Assinatura eletrônica (ZapSign) de um termo pendente. {@code iniciar} cria o documento a partir do
 * MODELO do termo, substituindo as variáveis, e devolve o sign_url para a cerimônia no app.
 * {@code processarAssinado} é chamado pelo webhook doc_signed: baixa o PDF assinado, guarda no S3,
 * cria o Documento no prontuário e marca o termo como ASSINADO. Só {@code .docx} é assinável.
 */
@Service
public class AssinaturaTermoService {

    /** Status em que um termo ainda pode ser (re)assinado. */
    private static final List<StatusTermoAssinatura> ASSINAVEIS =
            List.of(StatusTermoAssinatura.PENDENTE, StatusTermoAssinatura.TENTAR_NOVAMENTE);

    private final TermoAssinaturaRepository termoRepository;
    private final TermoProcedimentoRepository termoProcedimentoRepository;
    private final DocumentoRepository documentoRepository;
    private final ZapSignClient zapSign;
    private final StorageService storageService;
    private final TermoVariavelResolver variavelResolver;

    public AssinaturaTermoService(TermoAssinaturaRepository termoRepository,
            TermoProcedimentoRepository termoProcedimentoRepository, DocumentoRepository documentoRepository,
            ZapSignClient zapSign, StorageService storageService, TermoVariavelResolver variavelResolver) {
        this.termoRepository = termoRepository;
        this.termoProcedimentoRepository = termoProcedimentoRepository;
        this.documentoRepository = documentoRepository;
        this.zapSign = zapSign;
        this.storageService = storageService;
        this.variavelResolver = variavelResolver;
    }

    /** Inicia a assinatura de um único termo do paciente logado e devolve o sign_url da cerimônia. */
    @Transactional
    public String iniciar(Long termoId, Long pacienteId) {
        TermoAssinatura termo = termoRepository.findByIdAndProntuario_Agendamento_Paciente_Id(termoId, pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Termo não encontrado"));
        if (!ehAssinavel(termo.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este termo não está mais pendente de assinatura.");
        }
        Agendamento ag = termo.getProntuario().getAgendamento();
        List<ZapSignClient.CampoModelo> dados = variavelResolver.resolver(ag);
        ZapSignClient.DocumentoCriado doc = zapSign.criarDocViaModelo(
                garantirTemplate(termo.getTermoProcedimento()), signatario(ag.getPaciente()), dados, "termo-" + termo.getId());
        termo.setZapsignDocToken(doc.docToken());
        termo.setSignerToken(doc.signerToken());
        termoRepository.save(termo);
        return doc.signUrl();
    }

    /**
     * Inicia a assinatura EM LOTE de todos os termos pendentes de um prontuário (atendimento) do
     * paciente logado, numa cerimônia só: o 1º termo vira o documento principal e os demais entram
     * como documentos EXTRAS (mesmo sign_url). Guarda o token de cada termo para o webhook processar.
     */
    @Transactional
    public String iniciarLote(Long prontuarioId, Long pacienteId) {
        // Assináveis = pendentes de assinatura OU que falharam na ZapSign (recusa) e vão refazer.
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Agendamento_Paciente_IdAndStatusInOrderByCriadoEmAsc(
                        prontuarioId, pacienteId, ASSINAVEIS);
        if (termos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum termo pendente para assinar.");
        }
        Agendamento ag = termos.get(0).getProntuario().getAgendamento();
        List<ZapSignClient.CampoModelo> dados = variavelResolver.resolver(ag);

        // Documento principal = 1º termo.
        TermoAssinatura principal = termos.get(0);
        ZapSignClient.DocumentoCriado doc = zapSign.criarDocViaModelo(
                garantirTemplate(principal.getTermoProcedimento()), signatario(ag.getPaciente()), dados,
                "termo-" + principal.getId());
        principal.setZapsignDocToken(doc.docToken());
        principal.setSignerToken(doc.signerToken());
        termoRepository.save(principal);

        // Demais termos = documentos extras (anexados ao principal; mesma cerimônia).
        for (int i = 1; i < termos.size(); i++) {
            TermoAssinatura extra = termos.get(i);
            String extraToken = zapSign.anexarDocExtra(doc.docToken(), garantirTemplate(extra.getTermoProcedimento()), dados);
            extra.setZapsignDocToken(extraToken);
            termoRepository.save(extra);
        }
        return doc.signUrl();
    }

    /**
     * Marca como EM_CONFIRMACAO os termos assináveis (com token) do atendimento — chamado pelo app
     * assim que a cerimônia dispara o evento de assinatura concluída (feedback imediato: some o botão).
     * Não toca em termos já ASSINADO (evita corrida com o webhook). Devolve os termos do atendimento.
     */
    @Transactional
    public List<TermoAssinatura> marcarEmConfirmacao(Long prontuarioId, Long pacienteId) {
        List<TermoAssinatura> termos = termoRepository
                .findByProntuario_IdAndProntuario_Agendamento_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
        for (TermoAssinatura t : termos) {
            boolean iniciado = t.getZapsignDocToken() != null && !t.getZapsignDocToken().isBlank();
            if (iniciado && ehAssinavel(t.getStatus())) {
                t.setStatus(StatusTermoAssinatura.EM_CONFIRMACAO);
                termoRepository.save(t);
            }
        }
        return termos;
    }

    /** Endpoint leve: só lê os termos do atendimento (posse) — o app usa no loop de conferência. */
    @Transactional(readOnly = true)
    public List<TermoAssinatura> conferir(Long prontuarioId, Long pacienteId) {
        return termoRepository
                .findByProntuario_IdAndProntuario_Agendamento_Paciente_IdOrderByCriadoEmAsc(prontuarioId, pacienteId);
    }

    /**
     * Webhook doc_refused: a assinatura foi recusada na ZapSign. Joga os termos em confirmação/pendentes
     * do MESMO atendimento para TENTAR_NOVAMENTE (o app oferece refazer a cerimônia inteira). Não mexe
     * em termos já ASSINADO. Idempotente (no-op se o token não for de um termo nosso).
     */
    @Transactional
    public void processarRecusa(String zapsignDocToken) {
        if (zapsignDocToken == null || zapsignDocToken.isBlank()) {
            return;
        }
        TermoAssinatura ref = termoRepository.findByZapsignDocToken(zapsignDocToken).orElse(null);
        if (ref == null) {
            return;
        }
        List<TermoAssinatura> irmaos = termoRepository
                .findByProntuario_IdOrderByCriadoEmAsc(ref.getProntuario().getId());
        for (TermoAssinatura t : irmaos) {
            if (t.getStatus() == StatusTermoAssinatura.EM_CONFIRMACAO
                    || t.getStatus() == StatusTermoAssinatura.PENDENTE) {
                t.setStatus(StatusTermoAssinatura.TENTAR_NOVAMENTE);
                termoRepository.save(t);
            }
        }
    }

    private static boolean ehAssinavel(StatusTermoAssinatura status) {
        return ASSINAVEIS.contains(status);
    }

    /** Signatário da cerimônia = o paciente (assinatura em tela, sem e-mail automático). */
    private ZapSignClient.Signatario signatario(Paciente paciente) {
        return new ZapSignClient.Signatario(
                paciente.getNome(), digitos(paciente.getCpf()), "55", digitos(primeiroTelefone(paciente)),
                "assinaturaTela", "paciente-" + paciente.getId(), "Paciente", false);
    }

    /**
     * Webhook doc_signed: baixa o PDF assinado, guarda no S3, cria o Documento no prontuário e marca
     * o termo como ASSINADO. Idempotente (ignora se não é nosso, se já assinado ou se ainda pendente).
     */
    @Transactional
    public void processarAssinado(String zapsignDocToken) {
        if (zapsignDocToken == null || zapsignDocToken.isBlank()) {
            return;
        }
        // Re-busca o documento na ZapSign (não confia só no payload).
        JsonNode doc = zapSign.detalhar(zapsignDocToken);
        // Este documento (principal ou extra): processa se já tem PDF assinado.
        processarUm(zapsignDocToken, doc.path("signed_file").asText(null));
        // Assinatura em lote: se este é o PRINCIPAL, processa cada EXTRA pelo signed_file do extra_docs
        // (o webhook do extra não é garantido, e o extra_docs NÃO traz "status" — só token + signed_file).
        JsonNode extras = doc.path("extra_docs");
        if (extras.isArray()) {
            for (JsonNode e : extras) {
                processarUm(e.path("token").asText(null), e.path("signed_file").asText(null));
            }
        }
    }

    /** Processa UM documento assinado (principal ou extra): baixa o PDF, guarda e marca ASSINADO. */
    private void processarUm(String docToken, String signedFile) {
        if (docToken == null || docToken.isBlank() || signedFile == null || signedFile.isBlank()) {
            return; // sem documento, ou ainda sem PDF assinado
        }
        TermoAssinatura termo = termoRepository.findByZapsignDocToken(docToken).orElse(null);
        if (termo == null || termo.getStatus() == StatusTermoAssinatura.ASSINADO) {
            return;
        }
        byte[] pdf = zapSign.baixarArquivo(signedFile); // URL da ZapSign expira ~60min: baixa agora
        String url = storageService.salvarBytes(pdf, "application/pdf", "tcle-assinado", termo.getNome() + ".pdf");

        Documento documento = new Documento();
        documento.setProntuario(termo.getProntuario());
        documento.setNome(termo.getNome() + " (assinado)");
        documento.setUrl(url);
        documento.setStatusAnalise(StatusAnaliseDocumento.NAO_ANALISAVEL); // termo assinado não passa pela IA
        documentoRepository.save(documento);

        termo.setStatus(StatusTermoAssinatura.ASSINADO);
        termo.setSignedUrl(url);
        termo.setAssinadoEm(LocalDateTime.now());
        termoRepository.save(termo);
    }

    /** Garante o Modelo (template) do termo: usa o token salvo, registra o .docx sob demanda, ou bloqueia .doc. */
    private String garantirTemplate(TermoProcedimento tp) {
        if (tp == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Termo indisponível para assinatura (origem removida).");
        }
        if (tp.getZapsignTemplateToken() != null && !tp.getZapsignTemplateToken().isBlank()) {
            return tp.getZapsignTemplateToken();
        }
        if (!ehDocx(tp.getUrl(), tp.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Termo em formato .doc não pode ser assinado. Reenvie o arquivo em .docx.");
        }
        // .docx sem token (upload antigo ou falha no registro): registra agora.
        byte[] bytes = storageService.baixarBytes(tp.getUrl());
        if (bytes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível ler o arquivo do termo.");
        }
        String token = zapSign.criarTemplate(tp.getNome(), Base64.getEncoder().encodeToString(bytes));
        tp.setZapsignTemplateToken(token);
        termoProcedimentoRepository.save(tp);
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
