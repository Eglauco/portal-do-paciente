package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;

/**
 * Roteamento de login do paciente. Fase B: o vínculo cpf→inquilino é idempotente POR inquilino e um
 * mesmo CPF pode estar em N clínicas (identidade central — NÃO há mais "1 pessoa = 1 inquilino").
 * CPF nulo/vazio é ignorado.
 */
@SpringBootTest
class RoteamentoPacienteTest {

    private static final String CPF = "99988877700";
    private static final String SCHEMA_OUTRO = "inq_teste_rot_outro";
    private static final String TEL_REC = "11955550099";

    @Autowired
    private RoteamentoPacienteService service;
    @Autowired
    private PacienteLoginRepository pacienteLoginRepository;
    @Autowired
    private InquilinoService inquilinoService;
    @Autowired
    private InquilinoRepository inquilinoRepository;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteRepository pacienteRepository;

    @BeforeEach
    void setup() {
        limpar();
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void registraVinculoEhIdempotentePorInquilino() {
        Long padrao = inquilinoService.idPadrao();
        service.registrar(CPF, padrao);
        assertFalse(pacienteLoginRepository.findByCpf(CPF).isEmpty(), "vínculo criado");
        service.registrar(CPF, padrao); // 2x no MESMO inquilino não duplica
        assertEquals(1, pacienteLoginRepository.findByCpf(CPF).size(), "não duplica o vínculo do mesmo inquilino");
        assertEquals(padrao, pacienteLoginRepository.findByCpf(CPF).get(0).getInquilinoId());
    }

    @Test
    void mesmoCpfEmOutroInquilinoCriaSegundoVinculo() {
        service.registrar(CPF, inquilinoService.idPadrao());
        Inquilino outro = new Inquilino();
        outro.setNome("Outro");
        outro.setSchemaName(SCHEMA_OUTRO);
        Long outroId = inquilinoRepository.save(outro).getId();
        // Fase B: o mesmo CPF em outra clínica NÃO falha mais — cria um 2º vínculo (identidade central).
        service.registrar(CPF, outroId);
        assertEquals(2, pacienteLoginRepository.findByCpf(CPF).size(),
                "o mesmo CPF pode estar em duas clínicas (1→N)");
    }

    @Test
    void cpfNuloOuVazioEhIgnorado() {
        service.registrar(null, inquilinoService.idPadrao());
        service.registrar("", inquilinoService.idPadrao());
        service.registrar("  ", inquilinoService.idPadrao());
        assertTrue(pacienteLoginRepository.findByCpf(CPF).isEmpty(), "nada criado para CPF nulo/vazio");
    }

    @Test
    void reconciliarRemoveVinculoOrfao() {
        Long padrao = inquilinoService.idPadrao();
        service.registrar(CPF, padrao);
        assertFalse(pacienteLoginRepository.findByCpf(CPF).isEmpty(), "vínculo criado");
        // Nenhum paciente/responsável com esse CPF no schema → reconciliar remove o órfão.
        service.reconciliar(List.of(CPF), padrao);
        assertTrue(pacienteLoginRepository.findByCpf(CPF).isEmpty(), "vínculo órfão removido");
    }

    @Test
    void reconciliarMantemVinculoEnquantoAlguemUsaOCpf() {
        Long padrao = inquilinoService.idPadrao();
        // Cria um paciente com o CPF no schema (atalho: cria sem CPF e seta direto, como nos outros testes).
        pacienteRepository.buscarPorTelefoneNaLista(TEL_REC).forEach(p -> pacienteRepository.deleteById(p.getId()));
        Long pid = pacienteController.criar(new PacienteRequest("Teste Reconciliar", TEL_REC), null).getId();
        try {
            Paciente p = pacienteRepository.findById(pid).orElseThrow();
            p.setCpf(CPF);
            pacienteRepository.save(p);
            service.registrar(CPF, padrao);
            // Ainda há paciente com o CPF nesta clínica → NÃO remove (nunca apaga vínculo válido).
            service.reconciliar(List.of(CPF), padrao);
            assertFalse(pacienteLoginRepository.findByCpf(CPF).isEmpty(),
                    "vínculo mantido enquanto há paciente com o CPF");
            // Sumiu o paciente → o CPF fica órfão → reconciliar remove.
            pacienteRepository.deleteById(pid);
            service.reconciliar(List.of(CPF), padrao);
            assertTrue(pacienteLoginRepository.findByCpf(CPF).isEmpty(),
                    "vínculo removido depois que ninguém mais usa o CPF");
        } finally {
            pacienteRepository.findById(pid).ifPresent(x -> pacienteRepository.deleteById(pid));
        }
    }

    private void limpar() {
        pacienteLoginRepository.deleteAll(pacienteLoginRepository.findByCpf(CPF));
        inquilinoRepository.findBySchemaName(SCHEMA_OUTRO).ifPresent(inquilinoRepository::delete);
        pacienteRepository.buscarPorTelefoneNaLista(TEL_REC).forEach(p -> pacienteRepository.deleteById(p.getId()));
    }
}
