package com.example.pop.prontuario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tela "Prontuário Médico" (back-office): histórico do paciente em linha do tempo + resumo do
 * histórico por IA. O resumo é gerado AUTOMATICAMENTE ao analisar um documento novo (sem botão
 * manual). Sob /prontuario/** → papel ADMIN (a permissão por tela é no front).
 */
@RestController
@RequestMapping("/prontuario/medico")
public class ProntuarioMedicoController {

    private final ProntuarioMedicoService service;

    public ProntuarioMedicoController(ProntuarioMedicoService service) {
        this.service = service;
    }

    /** Cabeçalho do paciente + resumo do histórico + linha do tempo dos prontuários. */
    @GetMapping("/{pacienteId}")
    public HistoricoMedicoResponse historico(@PathVariable Long pacienteId) {
        return service.historico(pacienteId);
    }
}
