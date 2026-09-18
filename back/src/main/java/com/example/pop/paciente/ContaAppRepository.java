package com.example.pop.paciente;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaAppRepository extends JpaRepository<ContaApp, Long> {

    /** Conta pelo CPF (dígitos) — a chave da conta/sessão. */
    Optional<ContaApp> findByCpf(String cpf);

    /** Ids das contas cujos CPFs estão no conjunto (fan-out do push). */
    @Query("select c.id from ContaApp c where c.cpf in :cpfs")
    List<Long> findIdsByCpfIn(@Param("cpfs") Collection<String> cpfs);

    /** Existe alguma conta com aparelho ativo entre estes CPFs? (paciente alcançável via responsável). */
    @Query("select count(c) > 0 from ContaApp c where c.cpf in :cpfs and c.dispositivoAtivo is not null")
    boolean existeSessaoAtivaPorCpfs(@Param("cpfs") Collection<String> cpfs);
}
