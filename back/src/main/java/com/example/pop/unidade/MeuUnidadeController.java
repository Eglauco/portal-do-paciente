package com.example.pop.unidade;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.storage.StorageService;

/**
 * Perfil da unidade para o PACIENTE logado (app): aberto ao tocar no nome da unidade dentro da
 * conversa. Só devolve unidades a que o paciente tem acesso (404/403 caso contrário).
 */
@RestController
@RequestMapping("/meu/unidades")
public class MeuUnidadeController {

    private final UnidadeRepository repository;
    private final PacienteAcessoService acessoService;
    private final StorageService storageService;

    public MeuUnidadeController(UnidadeRepository repository, PacienteAcessoService acessoService,
            StorageService storageService) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.storageService = storageService;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public PerfilUnidadeResponse perfil(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.CHAT);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        acessoService.exigirUnidade(paciente, id); // paciente só vê unidade a que tem acesso
        Unidade unidade = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade não encontrada"));
        return PerfilUnidadeResponse.from(unidade, storageService);
    }
}
