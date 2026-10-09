package com.example.pop.inquilino;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/**
 * Gestão de inquilinos pelo SUPER-ADMIN (acessado "por fora" do sistema dos inquilinos). Opera
 * sempre no schema {@code public} (a tabela {@code inquilino} é da plataforma). Protegido por um
 * segredo fixo no header {@code X-SuperAdmin-Secret} (mesmo padrão dos endpoints {@code /dev/**}).
 *
 * <p>Endurecer depois (Fase posterior): login próprio, IP allowlist, auditoria de ações,
 * rate-limit, e atomicidade/rollback do provisionamento.
 */
@RestController
@RequestMapping("/superadmin/inquilinos")
public class SuperAdminController {

    private final byte[] segredo;
    private final InquilinoRepository inquilinoRepository;
    private final ProvisionamentoService provisionamentoService;

    public SuperAdminController(@Value("${app.superadmin.secret}") String segredo,
            InquilinoRepository inquilinoRepository, ProvisionamentoService provisionamentoService) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.inquilinoRepository = inquilinoRepository;
        this.provisionamentoService = provisionamentoService;
    }

    /** Lista os inquilinos cadastrados. */
    @GetMapping
    public List<InquilinoResponse> listar(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret) {
        conferirSegredo(secret);
        return inquilinoRepository.findAll().stream().map(InquilinoResponse::from).toList();
    }

    /** Cadastra um inquilino e PROVISIONA o schema dele (baseline limpo). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquilinoResponse criar(@RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret,
            @Valid @RequestBody CriarInquilinoRequest request) {
        conferirSegredo(secret);
        String schema = request.schemaName();
        if ("public".equals(schema)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O schema 'public' é da plataforma.");
        }
        if (inquilinoRepository.findBySchemaName(schema).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um inquilino com esse schema.");
        }

        // Registra primeiro (a unicidade do schema_name no public barra duplicata concorrente),
        // depois provisiona. Se o provisionamento falhar, desfaz o registro (best-effort).
        Inquilino inquilino = new Inquilino();
        inquilino.setNome(request.nome().trim());
        inquilino.setSchemaName(schema);
        inquilinoRepository.save(inquilino);
        try {
            provisionamentoService.provisionarInquilino(schema);
        } catch (RuntimeException e) {
            inquilinoRepository.delete(inquilino);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao provisionar o inquilino: " + e.getMessage(), e);
        }
        return InquilinoResponse.from(inquilino);
    }

    /** Confere o segredo do super-admin em tempo constante; 401 se ausente/errado. */
    private void conferirSegredo(String secret) {
        byte[] enviado = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(segredo, enviado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de super-admin inválida.");
        }
    }
}
