package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Roteamento de login do paciente (Fase 1.3a): o ponteiro cpf→inquilino é idempotente e barra o
 * mesmo CPF em dois inquilinos (1 pessoa = 1 inquilino). CPF nulo/vazio é ignorado.
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
    void registraPonteiroEhIdempotente() {
        Long padrao = inquilinoService.idPadrao();
        service.registrar(CPF, padrao);
        assertTrue(pacienteLoginRepository.findByCpf(CPF).isPresent(), "ponteiro criado");
        service.registrar(CPF, padrao); // 2x não duplica nem falha
        assertEquals(padrao, pacienteLoginRepository.findByCpf(CPF).orElseThrow().getInquilinoId());
    }

    @Test
    void cpfEmOutroInquilinoFalha() {
        service.registrar(CPF, inquilinoService.idPadrao());
        Inquilino outro = new Inquilino();
        outro.setNome("Outro");
        outro.setSchemaName(SCHEMA_OUTRO);
        Long outroId = inquilinoRepository.save(outro).getId();
        assertThrows(ResponseStatusException.class, () -> service.registrar(CPF, outroId),
                "mesmo CPF em outro inquilino deve falhar (1 pessoa = 1 inquilino)");
    }

    @Test
    void cpfNuloOuVazioEhIgnorado() {
        service.registrar(null, inquilinoService.idPadrao());
        service.registrar("", inquilinoService.idPadrao());
        service.registrar("  ", inquilinoService.idPadrao());
        // não lança nem cria nada
    }

    private void limpar() {
        pacienteLoginRepository.findByCpf(CPF).ifPresent(pacienteLoginRepository::delete);
        inquilinoRepository.findBySchemaName(SCHEMA_OUTRO).ifPresent(inquilinoRepository::delete);
    }
}
