package com.example.pop.motivofalta;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MotivoFaltaRepository extends JpaRepository<MotivoFalta, Long> {

    @Query(value = """
            select m from MotivoFalta m
            where (:id is null or m.id = :id)
              and lower(m.motivo) like lower(concat('%', :motivo, '%'))
            """,
            countQuery = """
            select count(m) from MotivoFalta m
            where (:id is null or m.id = :id)
              and lower(m.motivo) like lower(concat('%', :motivo, '%'))
            """)
    Page<MotivoFalta> search(@Param("id") Long id, @Param("motivo") String motivo, Pageable pageable);

    /** Motivos ativos, em ordem alfabética (para o app exibir na seleção da falta). */
    List<MotivoFalta> findByAtivoTrueOrderByMotivo();
}
