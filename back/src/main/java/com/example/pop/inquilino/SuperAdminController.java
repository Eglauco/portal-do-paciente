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
 * sempre no schema {@code public} (as tabelas {@code inquilino}/{@code usuario_login} são da
 * plataforma). Protegido por um segredo fixo no header {@code X-SuperAdmin-Secret} (mesmo padrão
 * dos endpoints {@code /dev/**}).
 *
 * <p>Endurecer depois: login próprio, IP allowlist, auditoria de ações, rate-limit, e atomicidade
 * real do provisionamento.
 */
@RestController
@RequestMapping("/superadmin/inquilinos")
public class SuperAdminController {

    private final byte[] segredo;
    private final InquilinoRepository inquilinoRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final ProvisionamentoService provisionamentoService;
    private final SeedInicialService seedInicialService;

    public SuperAdminController(@Value("${app.superadmin.secret}") String segredo,
            InquilinoRepository inquilinoRepository, UsuarioLoginRepository usuarioLoginRepository,
            ProvisionamentoService provisionamentoService, SeedInicialService seedInicialService) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.inquilinoRepository = inquilinoRepository;
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.provisionamentoService = provisionamentoService;
        this.seedInicialService = seedInicialService;
    }

    /** Lista os inquilinos cadastrados. */
    @GetMapping
    public List<InquilinoResponse> listar(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret) {
        conferirSegredo(secret);
        return inquilinoRepository.findAll().stream().map(InquilinoResponse::from).toList();
    }

    /** Cadastra um inquilino, PROVISIONA o schema (baseline limpo) e SEMEIA a 1ª unidade + o 1º admin. */
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
        if (usuarioLoginRepository.findByEmailIgnoreCase(request.adminEmail().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um login com esse e-mail.");
        }

        // Registra o inquilino (unicidade do schema no public barra duplicata concorrente), provisiona e semeia.
        // Se qualquer etapa falhar, desfaz tudo (best-effort — atomicidade real fica para uma fase posterior).
        Inquilino inquilino = new Inquilino();
        inquilino.setNome(request.nome().trim());
        inquilino.setSchemaName(schema);
        inquilinoRepository.save(inquilino);
        try {
            provisionamentoService.provisionarInquilino(schema);
            seedInicialService.semear(inquilino.getId(), schema, request.unidadeNome(),
                    request.adminNome(), request.adminEmail(), request.adminSenha());
        } catch (RuntimeException e) {
            desfazer(inquilino, schema, request.adminEmail().trim());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao provisionar/semear o inquilino: " + e.getMessage(), e);
        }
        return InquilinoResponse.from(inquilino);
    }

    /** Rollback best-effort de um provisionamento falho. */
    private void desfazer(Inquilino inquilino, String schema, String email) {
        try {
            usuarioLoginRepository.findByEmailIgnoreCase(email).ifPresent(usuarioLoginRepository::delete);
        } catch (RuntimeException ignore) {
            // best-effort
        }
        try {
            provisionamentoService.dropSchema(schema);
        } catch (RuntimeException ignore) {
            // best-effort
        }
        try {
            inquilinoRepository.delete(inquilino);
        } catch (RuntimeException ignore) {
            // best-effort
        }
    }

    /** Confere o segredo do super-admin em tempo constante; 401 se ausente/errado. */
    private void conferirSegredo(String secret) {
        byte[] enviado = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(segredo, enviado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de super-admin inválida.");
        }
    }
}
