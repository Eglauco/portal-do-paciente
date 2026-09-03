import '@/constants/polyfills';

import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { Stack, useRouter, useSegments } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { AppState, Platform } from 'react-native';
import 'react-native-reanimated';

import { LembretePopup } from '@/components/lembrete-popup';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { SessaoProvider, useSessao } from '@/hooks/use-sessao';
import { notificarAtualizacao } from '@/services/atualizacao';
import { ehChatAtivo } from '@/services/chat-ativo';
import { registrarParaPush } from '@/services/notificacoes';
import { navegarNotificacao } from '@/services/rota-notificacao';

// Mantém a splash nativa até sabermos se o paciente já está logado (sem piscar o login).
SplashScreen.preventAutoHideAsync();

export const unstable_settings = {
  initialRouteName: 'index',
  anchor: '(tabs)',
};

interface DadosNotificacao {
  tipo?: string;
  chatId?: number;
  postagemId?: number;
  manifestacaoId?: number;
  agendamentoId?: number;
}

// Como exibir a notificação quando o app está em primeiro plano.
Notifications.setNotificationHandler({
  handleNotification: async (notificacao) => {
    const dados = notificacao.request.content.data as DadosNotificacao;
    // Não mostra banner de nova mensagem se o paciente já está nessa conversa.
    if (dados?.tipo === 'CHAT' && dados.chatId != null && ehChatAtivo(dados.chatId)) {
      return {
        shouldShowBanner: false,
        shouldShowList: false,
        shouldPlaySound: false,
        shouldSetBadge: false,
      };
    }
    return {
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    };
  },
});

/** Ao tocar na notificação push, leva o paciente para a tela certa. */
function tratarToque(resposta: Notifications.NotificationResponse) {
  const dados = resposta.notification.request.content.data as DadosNotificacao;
  // Cada push traz só o id relevante do seu tipo; a rota é compartilhada com a lista.
  const id = dados?.chatId ?? dados?.manifestacaoId ?? dados?.postagemId ?? dados?.agendamentoId ?? null;
  navegarNotificacao(dados?.tipo, id);
}

/**
 * Redireciona conforme a sessão: sem login só a tela inicial (index); com login,
 * pula direto para o app. A splash só some quando já sabemos onde levar o paciente.
 */
function Navegacao() {
  const { sessao, carregando } = useSessao();
  const segments = useSegments();
  const roteador = useRouter();

  useEffect(() => {
    if (carregando) return;
    SplashScreen.hideAsync();
    const naTelaDeLogin = (segments as string[]).length === 0; // rota "/" (index)
    if (!sessao && !naTelaDeLogin) {
      roteador.replace('/');
    } else if (sessao && naTelaDeLogin) {
      roteador.replace('/(tabs)/agendamentos');
    }
  }, [sessao, carregando, segments, roteador]);

  return (
    <>
      <Stack>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="notificacoes" options={{ headerShown: false }} />
        <Stack.Screen name="perfil" options={{ headerShown: false }} />
        <Stack.Screen name="conversa/nova" options={{ headerShown: false }} />
        <Stack.Screen name="conversa/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="postagem/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="sau/nova" options={{ headerShown: false }} />
        <Stack.Screen name="sau/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="modal" options={{ presentation: 'modal', title: 'Modal' }} />
      </Stack>
      {/* Pop-up fixo de lembrete: só com sessão (usa endpoints do paciente). */}
      {sessao && <LembretePopup />}
    </>
  );
}

export default function RootLayout() {
  const colorScheme = useColorScheme();

  useEffect(() => {
    // Push remoto não é suportado na web (nem no Expo Go); só registra no nativo.
    if (Platform.OS === 'web') return;

    registrarParaPush();

    // App aberto por um toque na notificação (estava fechado).
    Notifications.getLastNotificationResponseAsync().then((resposta) => {
      if (resposta) tratarToque(resposta);
    });

    // Toque na notificação com o app aberto/em segundo plano.
    const sub = Notifications.addNotificationResponseReceivedListener(tratarToque);

    // Notificação chegou com o app em primeiro plano: atualiza a tela em foco.
    const recebido = Notifications.addNotificationReceivedListener(() => notificarAtualizacao());

    // App voltou de fato do SEGUNDO PLANO (ex.: paciente tocou na notificação):
    // atualiza. Só dispara em background→active — ignora o 'inactive' transitório
    // (diálogo de permissão, central de controle, alternador de apps).
    let estadoApp = AppState.currentState;
    const appState = AppState.addEventListener('change', (estado) => {
      if (estadoApp === 'background' && estado === 'active') notificarAtualizacao();
      estadoApp = estado;
    });

    return () => {
      sub.remove();
      recebido.remove();
      appState.remove();
    };
  }, []);

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <SessaoProvider>
        <Navegacao />
      </SessaoProvider>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}
