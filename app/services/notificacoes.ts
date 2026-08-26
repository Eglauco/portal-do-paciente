import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { API_URL } from '@/constants/api';

/**
 * Registra o aparelho para receber notificações push:
 * 1) cria o canal do Android;
 * 2) pede permissão;
 * 3) obtém o Expo Push Token;
 * 4) envia o token ao backend (/dispositivo).
 *
 * Observação: push remoto não funciona no Expo Go (SDK 53+). Use um
 * development build ou o APK standalone para testar.
 */
export async function registrarParaPush(): Promise<string | null> {
  // Emulador/simulador não recebe push remoto.
  if (!Device.isDevice) return null;

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Notificações',
      importance: Notifications.AndroidImportance.MAX,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#0E8C7F',
    });
  }

  const { status: atual } = await Notifications.getPermissionsAsync();
  let status = atual;
  if (status !== 'granted') {
    const solicitado = await Notifications.requestPermissionsAsync();
    status = solicitado.status;
  }
  if (status !== 'granted') return null;

  const projectId =
    Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) return null;

  let token: string;
  try {
    const resposta = await Notifications.getExpoPushTokenAsync({ projectId });
    token = resposta.data;
  } catch {
    return null;
  }

  // Registra no backend (idempotente).
  try {
    await fetch(`${API_URL}/dispositivo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
  } catch {
    // Falha ao registrar não deve quebrar o app.
  }

  return token;
}
