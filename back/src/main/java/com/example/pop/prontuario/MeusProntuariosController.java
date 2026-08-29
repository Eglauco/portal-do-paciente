package com.example.pop.prontuario;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.storage.DownloadUrlResponse;
import com.example.pop.storage.StorageService;

/**
 * Prontuários do paciente logado (app). Dado clínico mais sensível: tanto o
 * detalhe quanto o link de download exigem que o registro seja do paciente do
 * token (posse via agendamento.paciente). Não é do paciente -> 404.
 */
@RestController
@RequestMapping("/meu/prontuarios")
public class MeusProntuariosController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ProntuarioRepository repository;
    private final DocumentoRepository documentoRepository;
    private final StorageService storageService;
    private final PacienteAcessoService acessoService;

    public MeusProntuariosController(ProntuarioRepository repository, DocumentoRepository documentoRepository,
            StorageService storageService, PacienteAcessoService acessoService) {
        this.repository = repository;
        this.documentoRepository = documentoRepository;
        this.storageService = storageService;
        this.acessoService = acessoService;
    }

    /** Lista os prontuários do paciente logado. */
    @GetMapping
    public Pagina<ProntuarioResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id"));
        Page<Prontuario> resultado = repository.search("", pacienteId, null, pageable);
        List<ProntuarioResponse> content = resultado.getContent().stream().map(ProntuarioResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe (com documentos) de um prontuário do paciente logado. */
    @GetMapping("/{id}")
    public ProntuarioDetalheResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return repository.findByIdAndAgendamento_Paciente_Id(id, pacienteId)
                .map(ProntuarioDetalheResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prontuário não encontrado"));
    }

    /**
     * Link temporário de download de um documento do paciente logado. Só gera se
     * a URL pertencer a um documento de um prontuário do paciente (fecha o IDOR
     * do /storage/download-url genérico).
     */
    @GetMapping("/documento/download-url")
    public DownloadUrlResponse downloadDocumento(@AuthenticationPrincipal Jwt jwt, @RequestParam String url) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        if (!documentoRepository.existsByUrlAndProntuario_Agendamento_Paciente_Id(url, pacienteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado");
        }
        try {
            return new DownloadUrlResponse(storageService.gerarDownloadUrl(url));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível gerar o link do arquivo.");
        }
    }
}
