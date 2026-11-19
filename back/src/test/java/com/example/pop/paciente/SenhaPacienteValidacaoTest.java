package com.example.pop.paciente;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Regra da senha do paciente: livre, mínimo 8, máximo 64 caracteres (e 72 bytes do BCrypt). */
class SenhaPacienteValidacaoTest {

    private static void rejeita(String senha) {
        assertThatThrownBy(() -> PacienteAcessoService.validarSenha(senha))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static void aceita(String senha) {
        assertThatCode(() -> PacienteAcessoService.validarSenha(senha)).doesNotThrowAnyException();
    }

    @Test
    void aceitaSenhaLivreComOitoOuMaisCaracteres() {
        aceita("12345678");          // só números, 8
        aceita("abcdefgh");          // só letras
        aceita("Minha senha!");      // com espaço e símbolo
        aceita("ção ñ é ü 🙂 ok");   // acentos e emoji
        aceita("a".repeat(64));      // teto
    }

    @Test
    void rejeitaCurtaVaziaSoEspacosOuLongaDemais() {
        rejeita(null);
        rejeita("");
        rejeita("123456");           // o antigo PIN de 6 dígitos
        rejeita("1234567");          // 7
        rejeita("        ");         // 8 espaços
        rejeita("a".repeat(65));     // acima do teto
        rejeita("ç".repeat(40));     // 40 caracteres mas 80 bytes (> 72 do BCrypt)
    }
}
