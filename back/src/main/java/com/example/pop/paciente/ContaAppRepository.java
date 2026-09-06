package com.example.pop.paciente;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaAppRepository extends JpaRepository<ContaApp, Long> {

    Optional<ContaApp> findByTelefone(String telefone);

    /** Ids das contas cujos telefones estão no conjunto (fan-out do push). */
    @Query("select c.id from ContaApp c where c.telefone in :telefones")
    List<Long> findIdsByTelefoneIn(@Param("telefones") Collection<String> telefones);

    /** Existe alguma conta com aparelho ativo entre estes telefones? (paciente alcançável via responsável). */
    @Query("select count(c) > 0 from ContaApp c where c.telefone in :telefones and c.dispositivoAtivo is not null")
    boolean existeSessaoAtivaPorTelefones(@Param("telefones") Collection<String> telefones);
}
