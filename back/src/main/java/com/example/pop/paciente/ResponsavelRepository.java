package com.example.pop.paciente;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResponsavelRepository extends JpaRepository<Responsavel, Long> {

    /** Existe algum responsável com este telefone (dígitos)? Base do login por telefone. */
    boolean existsByTelefone(String telefone);

    /**
     * Este telefone é responsável ATIVO DESTE paciente? (autorização do perfil dependente).
     * Responsável inativo (soft-delete) não dá acesso — some do app, mas preserva a autoria.
     */
    boolean existsByTelefoneAndPaciente_IdAndAtivoTrue(String telefone, Long pacienteId);

    /**
     * O responsável DESTE paciente com este telefone (quem a conta representa ao comentar).
     * Ordena por id para o nome exibido ser estável quando há mais de um com o mesmo telefone.
     */
    Optional<Responsavel> findFirstByPaciente_IdAndTelefoneOrderByIdAsc(Long pacienteId, String telefone);

    /** Todos os responsáveis deste paciente (com as permissões) — usado no fan-out do push. */
    List<Responsavel> findByPaciente_Id(Long pacienteId);

    /** Pacientes (distintos) de quem este telefone é responsável ATIVO — os perfis "dependentes". */
    @Query("select distinct r.paciente from Responsavel r where r.telefone = :telefone and r.ativo = true")
    List<Paciente> pacientesPorTelefoneDoResponsavel(@Param("telefone") String telefone);

    /** Telefones (distintos, não nulos) dos responsáveis ATIVOS deste paciente — contas que o acessam. */
    @Query("select distinct r.telefone from Responsavel r "
            + "where r.paciente.id = :pacienteId and r.ativo = true and r.telefone is not null")
    List<String> telefonesDosResponsaveis(@Param("pacienteId") Long pacienteId);

    // ---------- Identidade por CPF (login por CPF) — espelham as consultas por telefone acima ----------

    /** Existe algum responsável com este CPF (dígitos)? Base do login por CPF. */
    boolean existsByCpf(String cpf);

    /** Este CPF é responsável ATIVO DESTE paciente? (autorização do perfil dependente). */
    boolean existsByCpfAndPaciente_IdAndAtivoTrue(String cpf, Long pacienteId);

    /** O responsável DESTE paciente com este CPF (quem a conta representa). Estável por id. */
    Optional<Responsavel> findFirstByPaciente_IdAndCpfOrderByIdAsc(Long pacienteId, String cpf);

    /** Pacientes (distintos) de quem este CPF é responsável ATIVO — os perfis "dependentes". */
    @Query("select distinct r.paciente from Responsavel r where r.cpf = :cpf and r.ativo = true")
    List<Paciente> pacientesPorCpfDoResponsavel(@Param("cpf") String cpf);

    /** CPFs (distintos, não nulos) dos responsáveis ATIVOS deste paciente — contas que o acessam. */
    @Query("select distinct r.cpf from Responsavel r "
            + "where r.paciente.id = :pacienteId and r.ativo = true and r.cpf is not null")
    List<String> cpfsDosResponsaveis(@Param("pacienteId") Long pacienteId);

    /** Telefones (canal do OTP) dos cadastros ATIVOS deste CPF de responsável, mais antigo primeiro. */
    @Query("select r.telefone from Responsavel r "
            + "where r.cpf = :cpf and r.ativo = true and r.telefone is not null order by r.id asc")
    List<String> telefonesPorCpfDoResponsavel(@Param("cpf") String cpf);

    /** Um cadastro ATIVO deste CPF de responsável (telefone do OTP + data de nascimento do login). */
    Optional<Responsavel> findFirstByCpfAndAtivoTrueOrderByIdAsc(String cpf);

    /** Todos os cadastros ATIVOS deste CPF de responsável (para conferir telefone + data no login). */
    List<Responsavel> findByCpfAndAtivoTrue(String cpf);
}
