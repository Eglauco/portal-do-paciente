package com.example.pop.configuracao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;

import jakarta.validation.Valid;

/**
 * CRUD das configurações (back-office). Só LISTAR + EDITAR o valor — as configurações
 * nascem no código/migration; não há criar nem excluir pela tela.
 */
@RestController
@RequestMapping("/configuracao")
public class ConfiguracaoController {

    private final ConfiguracaoService service;

    public ConfiguracaoController(ConfiguracaoService service) {
        this.service = service;
    }

    @GetMapping
    public Pagina<ConfiguracaoResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) TipoConfiguracao tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listar(busca, tipo, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConfiguracaoResponse> buscar(@PathVariable Long id) {
        return service.buscar(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracaoResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody ConfiguracaoRequest dados, @AuthenticationPrincipal Jwt jwt) {
        return service.atualizarValor(id, dados, uid(jwt))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Id do admin logado (claim "uid" do token) — para a auditoria de quem alterou. */
    private Long uid(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        if (uid instanceof Number numero) {
            return numero.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
    }
}
