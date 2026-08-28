import '@/constants/polyfills';

import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { router, Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Platform } from 'react-native';
import 'react-native-reanimated';

import { useColorScheme } from '@/hooks/use-color-scheme';
import { ehChatAtivo } from '@/services/chat-ativo';
import { registrarParaPush } from '@/services/notificacoes';

export const unstable_settings = {
  initialRouteName: 'index',
  anchor: '(tabs)',
};

interface DadosNotificacao {
  tipo?: string;
  chatId?: number;
  postagemId?: number;
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
      <Stack>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="notificacoes" options={{ headerShown: false }} />
        <Stack.Screen name="perfil" options={{ headerShown: false }} />
        <Stack.Screen name="conversa/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="postagem/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="modal" options={{ presentation: 'modal', title: 'Modal' }} />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}
