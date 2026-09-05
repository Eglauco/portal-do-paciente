package com.example.pop.paciente;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.storage.StorageService;
import com.example.pop.storage.UploadUrlResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Perfil do paciente logado (app): visualização SOMENTE-LEITURA dos dados do
 * cadastro + troca da foto (upload direto ao S3 na pasta "foto-paciente").
 * Sob /meu/** → papel PACIENTE; o paciente vem do token, nunca do cliente.
 */
@RestController
@RequestMapping("/meu/perfil")
public class MeuPerfilController {

    private static final String PASTA_FOTO = "foto-paciente";
    private static final Duration VALIDADE_FOTO = Duration.ofHours(1);

    private final PacienteRepository repository;
    private final PacienteAcessoService acessoService;
    private final StorageService storageService;

    public MeuPerfilController(PacienteRepository repository, PacienteAcessoService acessoService,
            StorageService storageService) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.storageService = storageService;
    }

    /** Dados do paciente logado (somente leitura) + link temporário da foto. */
    @GetMapping
    public MeuPerfilResponse meuPerfil(@AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        return MeuPerfilResponse.from(paciente, storageService.urlVisualizacao(paciente.getFotoUrl(), VALIDADE_FOTO));
    }

    /** URL pré-assinada (PUT) para o app enviar a foto direto ao S3 (pasta fixa "foto-paciente"). */
    @PostMapping("/foto/upload-url")
    public UploadUrlResponse gerarUploadFoto(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FotoUploadRequest req) {
        acessoService.pacienteDoToken(jwt); // valida a sessão do paciente
        try {
            return storageService.gerarUploadUrl(req.nomeArquivo(), req.contentType(), PASTA_FOTO);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível preparar o envio da foto.");
        }
    }

    /**
     * Persiste a foto enviada. Valida que a URL é da pasta "foto-paciente" (o app
     * não pode gravar como foto a URL de outro objeto) e remove a foto anterior.
     */
    @PutMapping("/foto")
    public MeuPerfilResponse salvarFoto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SalvarFotoRequest req) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        if (!storageService.urlNaPasta(req.url(), PASTA_FOTO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de foto inválida.");
        }
        String anterior = paciente.getFotoUrl();
        paciente.setFotoUrl(req.url());
        repository.save(paciente);
        if (anterior != null && !anterior.equals(req.url())) {
            try {
                storageService.excluirPorUrl(anterior);
            } catch (RuntimeException ignored) {
                // best-effort: falhar ao apagar a foto antiga não impede a troca
            }
        }
        return MeuPerfilResponse.from(paciente, storageService.urlVisualizacao(paciente.getFotoUrl(), VALIDADE_FOTO));
    }

    /** Remove a foto do paciente e apaga o objeto no S3 (best-effort). */
    @DeleteMapping("/foto")
    public MeuPerfilResponse removerFoto(@AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        String anterior = paciente.getFotoUrl();
        if (anterior != null) {
            paciente.setFotoUrl(null);
            repository.save(paciente);
            try {
                storageService.excluirPorUrl(anterior);
            } catch (RuntimeException ignored) {
                // best-effort: falha ao apagar no S3 não impede remover a referência
            }
        }
        return MeuPerfilResponse.from(paciente, null);
    }

    /** Nome do arquivo + tipo para assinar o upload (o app não escolhe a pasta). */
    public record FotoUploadRequest(@NotBlank String nomeArquivo, String contentType) {
    }

    /** URL pública do objeto recém-enviado ao S3 (retornada pelo /foto/upload-url). */
    public record SalvarFotoRequest(@NotBlank String url) {
    }
}
