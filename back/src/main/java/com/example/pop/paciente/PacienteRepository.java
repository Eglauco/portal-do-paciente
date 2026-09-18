package com.example.pop.paciente;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Paciente pelo CPF (dígitos) — a chave de identidade do login. */
    Optional<Paciente> findByCpf(String cpf);

    /** Pacientes cujo número está na lista de telefones — usado na limpeza de testes (telefone não é chave). */
    @Query("select p from Paciente p join p.telefonesAdicionais t where t = :numero")
    java.util.List<Paciente> buscarPorTelefoneNaLista(@Param("numero") String numero);

    // Checagens de unicidade (ignoram o próprio registro na edição, via id != :id).
    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByCnsAndIdNot(String cns, Long id);

    boolean existsByCodigoIntegracaoAndIdNot(String codigoIntegracao, Long id);

    boolean existsByProntuarioAndIdNot(String prontuario, Long id);

    @Query(value = """
            select p from Paciente p
            where (:id is null or p.id = :id)
              and (:situacao is null or p.situacao = :situacao)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
              and (:cpf = '' or p.cpf like concat('%', :cpf, '%'))
              and (:prontuario = '' or lower(p.prontuario) like lower(concat('%', :prontuario, '%')))
            """,
            countQuery = """
            select count(p) from Paciente p
            where (:id is null or p.id = :id)
              and (:situacao is null or p.situacao = :situacao)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
              and (:cpf = '' or p.cpf like concat('%', :cpf, '%'))
              and (:prontuario = '' or lower(p.prontuario) like lower(concat('%', :prontuario, '%')))
            """)
    Page<Paciente> search(@Param("id") Long id, @Param("situacao") SituacaoCadastro situacao,
            @Param("nome") String nome, @Param("cpf") String cpf,
            @Param("prontuario") String prontuario, Pageable pageable);

    /** Pacientes vinculados a uma unidade (fan-out da notificação de nova postagem). */
    java.util.List<Paciente> findByUnidades_Id(Long unidadeId);

    /** Pacientes liberados a usar o app (dashboard). */
    long countByAtivoTrue();

    /** Pacientes com sessão de app amarrada a um aparelho (= usando o app de fato). */
    long countByDispositivoAtivoIsNotNull();
}
