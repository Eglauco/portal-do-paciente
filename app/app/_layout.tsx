import '@/constants/polyfills';

import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { router, Stack, useRouter, useSegments } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Platform } from 'react-native';
import 'react-native-reanimated';

import { useColorScheme } from '@/hooks/use-color-scheme';
import { SessaoProvider, useSessao } from '@/hooks/use-sessao';
import { ehChatAtivo } from '@/services/chat-ativo';
import { registrarParaPush } from '@/services/notificacoes';

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

/** Ao tocar na notificação, leva o paciente para a tela certa. */
function tratarToque(resposta: Notifications.NotificationResponse) {
  const dados = resposta.notification.request.content.data as DadosNotificacao;
  switch (dados?.tipo) {
    case 'AGENDAMENTO':
    case 'FALTA':
      // Falta registrada: leva o paciente à lista de agendamentos para justificar.
      router.navigate('/(tabs)/agendamentos');
      break;
    case 'CHAT':
      if (dados.chatId != null) {
        router.navigate({ pathname: '/conversa/[id]', params: { id: String(dados.chatId) } });
      } else {
        router.navigate('/(tabs)/chat');
      }
      break;
    case 'NPS':
      router.navigate('/(tabs)/nps');
      break;
    case 'SAU':
      if (dados.manifestacaoId != null) {
        router.navigate({ pathname: '/sau/[id]', params: { id: String(dados.manifestacaoId) } });
      } else {
        router.navigate('/(tabs)/sau');
      }
      break;
    case 'PRONTUARIO':
      router.navigate('/(tabs)/prontuario');
      break;
    case 'POSTAGEM':
      if (dados.postagemId != null) {
        router.navigate({ pathname: '/postagem/[id]', params: { id: String(dados.postagemId) } });
      } else {
        router.navigate('/(tabs)/novidades');
      }
      break;
  }
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
    return () => sub.remove();
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
