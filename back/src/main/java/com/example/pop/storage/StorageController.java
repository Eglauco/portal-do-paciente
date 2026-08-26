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

    /** Gera a URL pré-assinada para o upload direto do navegador ao S3. */
    @PostMapping("/upload-url")
    public UploadUrlResponse gerarUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        try {
            return storageService.gerarUploadUrl(request.nomeArquivo(), request.contentType());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível preparar o upload. Verifique a configuração do armazenamento.");
        }
    }

    /** Gera uma URL pré-assinada (GET) temporária para visualizar o arquivo. */
    @GetMapping("/download-url")
    public DownloadUrlResponse gerarDownloadUrl(@RequestParam String url) {
        try {
            return new DownloadUrlResponse(storageService.gerarDownloadUrl(url));
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
