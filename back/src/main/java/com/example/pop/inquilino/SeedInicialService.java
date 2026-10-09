package com.example.pop.inquilino;

import java.util.HashSet;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.pop.perfil.Perfil;
import com.example.pop.perfil.PerfilRepository;
import com.example.pop.tenant.TenantContext;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Semeia o 1º admin + a 1ª unidade de um inquilino recém-provisionado, para o cliente conseguir
 * entrar. No schema do inquilino: cria a unidade, liga o perfil Administrador (já existe no baseline)
 * a ela e cria o usuário admin (perfil Administrador + unidade ativa). No public: o ponteiro de
 * roteamento {@code usuario_login} (email → inquilino) usado pelo login em 2 fases.
 *
 * <p>NÃO é {@code @Transactional}: cada {@code save} abre a própria transação, para pegar a conexão
 * JÁ com o {@code search_path} do schema corrente (o {@link TenantContext} muda no meio do método —
 * uma transação única reusaria a conexão do schema inicial e gravaria no lugar errado).
 */
@Service
public class SeedInicialService {

    private final UnidadeRepository unidadeRepository;
    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedInicialService(UnidadeRepository unidadeRepository, PerfilRepository perfilRepository,
            UsuarioRepository usuarioRepository, UsuarioLoginRepository usuarioLoginRepository,
            PasswordEncoder passwordEncoder) {
        this.unidadeRepository = unidadeRepository;
        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void semear(Long inquilinoId, String schema, String unidadeNome,
            String adminNome, String adminEmail, String adminSenha) {
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(schema);
        try {
            Unidade unidade = new Unidade();
            unidade.setNome(unidadeNome.trim());
            unidadeRepository.save(unidade);

            Perfil administrador = perfilRepository.findByNomeIgnoreCase("Administrador")
                    .orElseThrow(() -> new IllegalStateException(
                            "Perfil Administrador ausente no schema do inquilino: " + schema));
            administrador.getUnidades().add(unidade);
            perfilRepository.save(administrador);

            Usuario admin = new Usuario();
            admin.setNome(adminNome.trim());
            admin.setEmail(adminEmail.trim());
            admin.setSenhaHash(passwordEncoder.encode(adminSenha));
            admin.setUnidade(unidade);
            admin.setPerfis(new HashSet<>(List.of(administrador)));
            usuarioRepository.save(admin);
        } finally {
            if (anterior != null) {
                TenantContext.definir(anterior);
            } else {
                TenantContext.limpar();
            }
        }

        // Ponteiro de roteamento no public (DEPOIS do tenant pronto). usuario_login tem @Table(schema="public").
        UsuarioLogin login = new UsuarioLogin();
        login.setEmail(adminEmail.trim());
        login.setInquilinoId(inquilinoId);
        usuarioLoginRepository.save(login);
    }
}
