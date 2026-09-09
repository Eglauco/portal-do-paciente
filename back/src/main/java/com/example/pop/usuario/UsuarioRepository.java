package com.example.pop.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Quantos usuários têm o perfil informado (usado para bloquear a exclusão de um perfil em uso). */
    long countByPerfis_Id(Long perfilId);

    /**
     * Ids dos admins ELEGÍVEIS a uma notificação: cujos perfis (união) liberam a TELA
     * informada E dão acesso à UNIDADE do evento. Nativa para casar direto com as tabelas
     * de junção do RBAC (usuario_perfil / perfil_tela / perfil_unidade).
     */
    @Query(value = """
            select u.id from usuario u
            where exists (
                select 1 from usuario_perfil up
                join perfil_tela pt on pt.perfil_id = up.perfil_id
                where up.usuario_id = u.id and pt.tela = :tela)
              and exists (
                select 1 from usuario_perfil up2
                join perfil_unidade pu on pu.perfil_id = up2.perfil_id
                where up2.usuario_id = u.id and pu.unidade_id = :unidadeId)
            """, nativeQuery = true)
    List<Long> idsComAcesso(@Param("tela") String tela, @Param("unidadeId") Long unidadeId);

    @Query(value = """
            select u from Usuario u
            left join fetch u.unidade
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and lower(u.email) like lower(concat('%', :email, '%'))
            """,
            countQuery = """
            select count(u) from Usuario u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and lower(u.email) like lower(concat('%', :email, '%'))
            """)
    Page<Usuario> search(@Param("id") Long id, @Param("nome") String nome, @Param("email") String email,
            Pageable pageable);
}
