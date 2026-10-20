package com.example.pop.storage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Gera a URL pré-assinada para o upload direto do navegador ao S3. Com {@code unidadeId} no corpo,
     * grava no S3 daquela UNIDADE (prontuário, postagem…); sem ele, no S3 PADRÃO do inquilino (fotos de
     * profissional, logomarca, modelos de TCLE).
     */
    @PostMapping("/upload-url")
    public UploadUrlResponse gerarUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        EscopoS3 escopo = request.unidadeId() != null
                ? EscopoS3.unidade(request.unidadeId())
                : EscopoS3.inquilinoPadrao();
        try {
            return storageService.gerarUploadUrl(escopo, request.nomeArquivo(), request.contentType(), request.pasta());
        } catch (ResponseStatusException e) {
            throw e; // "armazenamento não configurado" (422) e afins passam direto para o front
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível preparar o upload. Verifique a configuração do armazenamento.");
        }
    }

    /**
     * Gera uma URL pré-assinada (GET) temporária. Sem {@code nome}: visualização inline. Com {@code nome}: força o
     * download (Content-Disposition: attachment) com esse nome de arquivo.
     */
    @GetMapping("/download-url")
    public DownloadUrlResponse gerarDownloadUrl(@RequestParam String url,
            @RequestParam(required = false) String nome) {
        try {
            return new DownloadUrlResponse(storageService.gerarDownloadUrl(url, nome));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível gerar o link do arquivo.");
        }
    }

    /** Exclui um objeto no S3 a partir da sua URL. */
    @DeleteMapping
    public ResponseEntity<Void> excluir(@RequestParam String url) {
        try {
            storageService.excluirPorUrl(url);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível excluir o arquivo no S3.");
        }
    }
}
