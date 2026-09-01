package com.example.pop.sau;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManifestacaoMensagemRepository extends JpaRepository<ManifestacaoMensagem, Long> {

    /** Thread completa da manifestação, em ordem cronológica. */
    List<ManifestacaoMensagem> findByManifestacaoIdOrderByCriadoEmAsc(Long manifestacaoId);

    /** Última mensagem (para o resumo na listagem). */
    ManifestacaoMensagem findFirstByManifestacaoIdOrderByCriadoEmDesc(Long manifestacaoId);
}
