package com.example.pop.prontuario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /**
     * Confere se a URL corresponde a um documento de um prontuário do paciente
     * (documento -> prontuário -> agendamento -> paciente). Usado para autorizar
     * o download escopado e fechar o IDOR do /storage/download-url.
     */
    boolean existsByUrlAndProntuario_Agendamento_Paciente_Id(String url, Long pacienteId);
}
