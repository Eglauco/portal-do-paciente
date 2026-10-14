package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Roteamento de login do paciente. Fase B: o vínculo cpf→inquilino é idempotente POR inquilino e um
 * mesmo CPF pode estar em N clínicas (identidade central — NÃO há mais "1 pessoa = 1 inquilino").
 * CPF nulo/vazio é ignorado.
 */
@SpringBootTest
class RoteamentoPacienteTest {

    private static final String CPF = "99988877700";
    private static final String SCHEMA_OUTRO = "inq_teste_rot_outro";

    @Autowired
    private RoteamentoPacienteService service;
    @Autowired
    private PacienteLoginRepository pacienteLoginRepository;
    @Autowired
    private InquilinoService inquilinoService;
    @Autowired
    private InquilinoRepository inquilinoRepository;

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

    private void limpar() {
        pacienteLoginRepository.deleteAll(pacienteLoginRepository.findByCpf(CPF));
        inquilinoRepository.findBySchemaName(SCHEMA_OUTRO).ifPresent(inquilinoRepository::delete);
    }
}
