import { Platform } from 'react-native';

/**
 * URL base da API (back-end Spring Boot).
 *
 * Precedência:
 * 1) EXPO_PUBLIC_API_URL (definida no .env.local em dev, ou no eas.json no build);
 * 2) em build de produção (APK/AAB): o backend hospedado no Railway;
 * 3) em desenvolvimento: hosts locais padrão.
 *
 * Dev local:
 * - iOS Simulator / Web: http://localhost:8080
 * - Android Emulator: http://10.0.2.2:8080
 * - Dispositivo físico (Expo Go): defina EXPO_PUBLIC_API_URL no .env.local com o
 *   IP da sua máquina, ex.: EXPO_PUBLIC_API_URL=http://192.168.0.10:8080
 */

/** Backend hospedado no Railway (produção). Ajuste aqui se o domínio mudar. */
const API_PRODUCAO = 'https://amiable-flow-production-a405.up.railway.app';

const PADRAO_LOCAL = Platform.select({
  android: 'http://10.0.2.2:8080',
  default: 'http://localhost:8080',
});

export const API_URL =
  process.env.EXPO_PUBLIC_API_URL ?? (__DEV__ ? PADRAO_LOCAL : API_PRODUCAO);
