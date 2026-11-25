package com.example.pop.agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.paciente.Paciente;

/** Regras puras do convite manual: celulares do paciente, formatação/máscara e validação dos números escolhidos. */
class HorarioConviteRegrasTest {

    @Test
    void celularesDoPacienteSoCelularSemRepetirEmE164() {
        Paciente p = new Paciente();
        p.setTelefonesAdicionais(new ArrayList<>(List.of(
                "(18) 98817-2191", "1832223344", "+55 18 98817-2191", "11 99812-1675", "")));
        assertThat(ConviteAppService.celularesDoPaciente(p)).containsExactly("+5518988172191", "+5511998121675");
        assertThat(ConviteAppService.celularesDoPaciente(new Paciente())).isEmpty();
        assertThat(ConviteAppService.celularesDoPaciente(null)).isEmpty();
    }

    @Test
    void exibeEMascaraONumero() {
        assertThat(ConviteAppService.exibir("+5518988172191")).isEqualTo("(18) 98817-2191");
        assertThat(ConviteAppService.mascarar("+5518988172191")).isEqualTo("(18) •••••-2191");
        assertThat(ConviteAppService.mascarar(null)).isEqualTo("•••");
    }

    @Test
    void validaQueOsNumerosSaoDoCadastro() {
        List<String> celulares = List.of("+5518988172191", "+5511998121675");
        assertThat(HorarioConviteService.validarNumeros(List.of("(18) 98817-2191", "+5518988172191"), celulares))
                .containsExactly("+5518988172191");
        assertThatThrownBy(() -> HorarioConviteService.validarNumeros(List.of("+5511900000000"), celulares))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(() -> HorarioConviteService.validarNumeros(List.of(" "), celulares))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> HorarioConviteService.validarNumeros(null, celulares))
                .isInstanceOf(ResponseStatusException.class);
    }
}
