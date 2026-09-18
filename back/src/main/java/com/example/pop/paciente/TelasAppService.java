package com.example.pop.paciente;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/**
 * Habilitação GLOBAL das telas do app (kill switch por {@link FuncionalidadeApp}). Lê a config
 * booleana de cada tela togglável com FAIL-OPEN (config ausente/quebrada → habilitada, para nunca
 * sumir uma tela por erro de configuração). Meu Perfil (e qualquer funcionalidade sem chave) é
 * sempre habilitada. A leitura reaproveita o cache do {@link ConfiguracaoService} (config é MUITO
 * lida — a cada request /meu/** e a cada SUBSCRIBE do WebSocket).
 *
 * <p>O flag só faz short-circuit de LEITURA de acesso; nunca toca em {@code Responsavel.permissoes}.
 * Por isso religar uma tela restaura tudo automaticamente (vínculos preservados).
 */
@Service
public class TelasAppService {

    /** Funcionalidade → chave de configuração da habilitação. As sem chave são sempre habilitadas. */
    private static final Map<FuncionalidadeApp, String> CHAVE = new EnumMap<>(FuncionalidadeApp.class);
    static {
        CHAVE.put(FuncionalidadeApp.AGENDAMENTOS, ChaveConfiguracao.APP_TELA_AGENDAMENTOS_HABILITADA);
        CHAVE.put(FuncionalidadeApp.CHAT, ChaveConfiguracao.APP_TELA_CHAT_HABILITADA);
        CHAVE.put(FuncionalidadeApp.SAU, ChaveConfiguracao.APP_TELA_SAU_HABILITADA);
        CHAVE.put(FuncionalidadeApp.REDE_SOCIAL, ChaveConfiguracao.APP_TELA_REDE_SOCIAL_HABILITADA);
        CHAVE.put(FuncionalidadeApp.PRONTUARIO, ChaveConfiguracao.APP_TELA_PRONTUARIO_HABILITADA);
        CHAVE.put(FuncionalidadeApp.NPS, ChaveConfiguracao.APP_TELA_NPS_HABILITADA);
        // MEU_PERFIL: sem chave → sempre habilitada.
    }

    private final ConfiguracaoService configuracaoService;

    public TelasAppService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /** A tela está habilitada globalmente? Fail-open: config ausente/quebrada → habilitada. */
    public boolean habilitada(FuncionalidadeApp funcionalidade) {
        String chave = funcionalidade == null ? null : CHAVE.get(funcionalidade);
        if (chave == null) {
            return true; // sem toggle (ex.: MEU_PERFIL): sempre visível
        }
        try {
            return configuracaoService.lerBooleano(chave);
        } catch (RuntimeException e) {
            return true; // fail-open: nunca some uma tela por config faltando/quebrada
        }
    }

    /** Estado de TODAS as funcionalidades (o app/back-office decidem o que ocultar). */
    public Map<FuncionalidadeApp, Boolean> todas() {
        Map<FuncionalidadeApp, Boolean> mapa = new EnumMap<>(FuncionalidadeApp.class);
        for (FuncionalidadeApp f : FuncionalidadeApp.values()) {
            mapa.put(f, habilitada(f));
        }
        return mapa;
    }
}
