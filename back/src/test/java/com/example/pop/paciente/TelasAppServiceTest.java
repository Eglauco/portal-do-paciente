package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/** Kill-switch global das telas: leitura da config com FAIL-OPEN e telas sem chave sempre ligadas. */
@ExtendWith(MockitoExtension.class)
class TelasAppServiceTest {

    @Mock
    private ConfiguracaoService configuracaoService;

    @InjectMocks
    private TelasAppService telasAppService;

    @Test
    void habilitadaRefleteAConfig() {
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_AGENDAMENTOS_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_NPS_HABILITADA)).thenReturn(false);

        assertTrue(telasAppService.habilitada(FuncionalidadeApp.AGENDAMENTOS));
        assertFalse(telasAppService.habilitada(FuncionalidadeApp.NPS));
    }

    @Test
    void telaSemChaveEhSempreHabilitada() {
        // MEU_PERFIL não tem toggle → sempre ligada, sem tocar na config.
        assertTrue(telasAppService.habilitada(FuncionalidadeApp.MEU_PERFIL));
        assertTrue(telasAppService.habilitada(null));
    }

    @Test
    void configQuebradaFazFailOpen() {
        // Config ausente/tipo errado (RuntimeException) NUNCA some uma tela: fail-open → habilitada.
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_CHAT_HABILITADA))
                .thenThrow(new IllegalStateException("chave sem valor"));

        assertTrue(telasAppService.habilitada(FuncionalidadeApp.CHAT));
    }

    @Test
    void todasCobreAsSeteFuncionalidades() {
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_AGENDAMENTOS_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_CHAT_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_SAU_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_REDE_SOCIAL_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_PRONTUARIO_HABILITADA)).thenReturn(true);
        when(configuracaoService.lerBooleano(ChaveConfiguracao.APP_TELA_NPS_HABILITADA)).thenReturn(false);

        Map<FuncionalidadeApp, Boolean> todas = telasAppService.todas();

        assertEquals(FuncionalidadeApp.values().length, todas.size());
        assertTrue(todas.get(FuncionalidadeApp.MEU_PERFIL)); // sem chave → sempre ligada
        assertFalse(todas.get(FuncionalidadeApp.NPS));
        assertTrue(todas.get(FuncionalidadeApp.AGENDAMENTOS));
    }
}
