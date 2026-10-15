package com.example.pop.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    boolean existsByToken(String token);

    /**
     * Registra/atualiza o aparelho pelo token de forma ATÔMICA e à prova de corrida: o
     * {@code ON CONFLICT (token)} resolve duas chamadas concorrentes com o MESMO token (ex.: o app
     * re-registra o push ao trocar de clínica/perfil) sem estourar o unique {@code dispositivo_token_key}
     * — ao contrário do find-then-save, que sob concorrência insere duplicado e dá 23505. Mantém o dono
     * anterior quando o novo vem nulo (COALESCE) e não mexe no {@code criado_em} na atualização. A tabela
     * {@code dispositivo} é por-inquilino: sem qualificar o schema, resolve pelo search_path do tenant.
     */
    @Modifying
    @Query(value = """
            INSERT INTO dispositivo (token, conta_id, paciente_id, criado_em)
            VALUES (:token, :contaId, :pacienteId, now())
            ON CONFLICT (token) DO UPDATE SET
              conta_id = COALESCE(EXCLUDED.conta_id, dispositivo.conta_id),
              paciente_id = COALESCE(EXCLUDED.paciente_id, dispositivo.paciente_id)
            """, nativeQuery = true)
    void registrarOuAtualizar(@Param("token") String token, @Param("contaId") Long contaId,
            @Param("pacienteId") Long pacienteId);

    List<Dispositivo> findByToken(String token);

    Optional<Dispositivo> findFirstByToken(String token);

    /**
     * Aparelhos legados de um paciente AINDA não migrados para o modelo por conta
     * (conta_id nulo). Escopar por conta_id nulo evita colidir com aparelhos de
     * outras contas que por acaso tenham o mesmo paciente_id.
     */
    List<Dispositivo> findByPacienteIdAndContaIdIsNull(Long pacienteId);

    /** Aparelhos de uma conta (para desvincular no logout). */
    List<Dispositivo> findByContaId(Long contaId);

    /** Aparelhos de um conjunto de contas (fan-out do push por conta). */
    List<Dispositivo> findByContaIdIn(Collection<Long> contaIds);
}
