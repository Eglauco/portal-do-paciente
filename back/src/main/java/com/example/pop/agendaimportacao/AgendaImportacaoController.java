package com.example.pop.agendaimportacao;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.export.ExportacaoService;

/**
 * Importação de agenda por Excel. Sob /agenda-importacao (ADMIN; registrado no SecurityConfig):
 * baixar a planilha-modelo, subir para ver o PREVIEW (não grava) e CONFIRMAR (grava a agenda + horários e
 * notifica os pacientes). A unidade executante é a do usuário logado ({@code unidadeId}, enviado pelo front).
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
     * os cadastros localizados por id/código e os erros por campo. A unidade executante é a do usuário logado
     * ({@code unidadeId}, enviado pelo front). Não grava nada.
     */
    @PostMapping("/preview")
    public AgendaImportPreviewResponse preview(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) Long unidadeId) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Selecione uma planilha .xlsx.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome != null && !nome.toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A planilha precisa ser um arquivo .xlsx. Baixe a planilha de exemplo e preencha sobre ela.");
        }
        try {
            return service.preview(arquivo.getBytes(), nome, unidadeId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível ler a planilha enviada.");
        }
    }

    /**
     * CONFIRMA a importação (Fase 2): re-valida no servidor e, se não houver erro, CRIA a agenda + os horários
     * (unidade = a do usuário logado) e NOTIFICA cada paciente. Recusa (422) se houver qualquer erro.
     */
    @PostMapping("/confirmar")
    public AgendaImportResultadoResponse confirmar(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) Long unidadeId, @AuthenticationPrincipal Jwt jwt) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Selecione uma planilha .xlsx.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome != null && !nome.toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A planilha precisa ser um arquivo .xlsx. Baixe a planilha de exemplo e preencha sobre ela.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível ler a planilha enviada.");
        }
        // Grava numa transação (lança 422 se houver erro de validação).
        AgendaImportacaoService.ResultadoImportacao r = service.persistir(bytes, unidadeId, uidDoToken(jwt));
        // Notifica os pacientes FORA da transação (best-effort): a importação já está gravada.
        service.notificarImportados(r.horarioIds());
        return new AgendaImportResultadoResponse(r.agendaId(), r.horarioIds().size());
    }

    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }
}
