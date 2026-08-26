import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { router, Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import 'react-native-reanimated';

import { useColorScheme } from '@/hooks/use-color-scheme';
import { registrarParaPush } from '@/services/notificacoes';

export const unstable_settings = {
  initialRouteName: 'index',
  anchor: '(tabs)',
};

// Como exibir a notificação quando o app está em primeiro plano.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/** Ao tocar na notificação, leva o paciente para a tela certa. */
function tratarToque(resposta: Notifications.NotificationResponse) {
  const dados = resposta.notification.request.content.data as { tipo?: string };
  if (dados?.tipo === 'AGENDAMENTO') {
    router.navigate('/(tabs)/agendamentos');
  }
}

export default function RootLayout() {
  const colorScheme = useColorScheme();

  useEffect(() => {
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
        <Stack.Screen name="modal" options={{ presentation: 'modal', title: 'Modal' }} />
      </Stack>
      <StatusBar style="auto" />
    </ThemeProvider>
  );
}
