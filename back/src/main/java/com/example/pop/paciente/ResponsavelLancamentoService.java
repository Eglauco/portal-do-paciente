package com.example.pop.paciente;

import org.springframework.stereotype.Service;

import com.example.pop.agendamento.AgendamentoLogRepository;
import com.example.pop.chat.MensagemRepository;
import com.example.pop.nps.NpsRepository;
import com.example.pop.postagem.ComentarioRepository;
import com.example.pop.sau.ManifestacaoMensagemRepository;
import com.example.pop.sau.ManifestacaoRepository;

/**
 * Descobre se um responsável tem QUALQUER lançamento no sistema (chat, SAU, feed,
 * troca de status de agendamento, NPS, ou evento de auditoria). Enquanto tiver, ele NÃO
 * pode ser removido — só inativado — para preservar a autoria histórica (a remoção zerava
 * o responsavel_id e a autoria virava do paciente).
 */
@Service
public class ResponsavelLancamentoService {

    private final ComentarioRepository comentarioRepository;
    private final ManifestacaoRepository manifestacaoRepository;
    private final ManifestacaoMensagemRepository manifestacaoMensagemRepository;
    private final MensagemRepository mensagemRepository;
    private final AgendamentoLogRepository agendamentoLogRepository;
    private final NpsRepository npsRepository;
    private final PacienteLogRepository pacienteLogRepository;

    public ResponsavelLancamentoService(ComentarioRepository comentarioRepository,
            ManifestacaoRepository manifestacaoRepository,
            ManifestacaoMensagemRepository manifestacaoMensagemRepository,
            MensagemRepository mensagemRepository,
            AgendamentoLogRepository agendamentoLogRepository,
            NpsRepository npsRepository,
            PacienteLogRepository pacienteLogRepository) {
        this.comentarioRepository = comentarioRepository;
        this.manifestacaoRepository = manifestacaoRepository;
        this.manifestacaoMensagemRepository = manifestacaoMensagemRepository;
        this.mensagemRepository = mensagemRepository;
        this.agendamentoLogRepository = agendamentoLogRepository;
        this.npsRepository = npsRepository;
        this.pacienteLogRepository = pacienteLogRepository;
    }

    /** True se o responsável tem qualquer lançamento (curto-circuita no primeiro encontrado). */
    public boolean temLancamentos(Long responsavelId) {
        if (responsavelId == null) {
            return false;
        }
        return comentarioRepository.existsByResponsavelId(responsavelId)
                || manifestacaoRepository.existsByResponsavelId(responsavelId)
                || manifestacaoMensagemRepository.existsByResponsavelId(responsavelId)
                || mensagemRepository.existsByResponsavelId(responsavelId)
                || agendamentoLogRepository.existsByResponsavel_Id(responsavelId)
                || npsRepository.existsByResponsavelId(responsavelId)
                || pacienteLogRepository.existsByResponsavel_Id(responsavelId);
    }
}
