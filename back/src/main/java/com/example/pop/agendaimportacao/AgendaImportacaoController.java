package com.example.pop.agendaimportacao;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.export.ExportacaoService;

/**
 * Importação de agenda por Excel — FASE 1 (só preview). Sob /agenda-importacao (ADMIN; registrado no
 * SecurityConfig). Dois endpoints: baixar a planilha-modelo e subir a planilha preenchida para ver o preview
 * validado. NADA é persistido aqui — a confirmação da importação (gravar a agenda + horários) vem na Fase 2.
 */
@RestController
@RequestMapping("/agenda-importacao")
public class AgendaImportacaoController {

    private final AgendaImportacaoService service;
    private final AgendaModeloPlanilha modelo;

    public AgendaImportacaoController(AgendaImportacaoService service, AgendaModeloPlanilha modelo) {
        this.service = service;
        this.modelo = modelo;
    }

    /** Baixa a planilha de exemplo (.xlsx) para o cliente preencher. */
    @GetMapping("/modelo")
    public ResponseEntity<byte[]> modelo() {
        byte[] arquivo = modelo.gerar();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-agenda.xlsx\"")
                .body(arquivo);
    }

    /**
     * Recebe a planilha preenchida e devolve o PREVIEW: cabeçalho da agenda + todas as linhas de horário, com
     * os campos resolvidos contra os cadastros e os erros por campo. Não grava nada.
     */
    @PostMapping("/preview")
    public AgendaImportPreviewResponse preview(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Selecione uma planilha .xlsx.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome != null && !nome.toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A planilha precisa ser um arquivo .xlsx. Baixe a planilha de exemplo e preencha sobre ela.");
        }
        try {
            return service.preview(arquivo.getBytes(), nome);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível ler a planilha enviada.");
        }
    }
}
