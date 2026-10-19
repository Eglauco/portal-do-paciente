import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';

import { API_URL } from '@/constants/api';

/**
 * Entrega do chat (2º "check" ✓✓) com o app minimizado/morto no Android é confirmada por um
 * FirebaseMessagingService NATIVO (Kotlin) — ver plugins/withAckMessagingService.js. Esse serviço
 * precisa saber EM QUAL backend confirmar a entrega. Em vez de um endereço fixo, o app grava aqui a
 * URL base que está usando (em dev, o IP do notebook descoberto pelo Metro; em produção, o Railway),
 * e o serviço nativo lê esse arquivo na hora de confirmar. Assim os testes funcionam 100% locais.
 *
 * O caminho casa com o lado Kotlin: em expo-file-system (SDK 54) documentDirectory == context.filesDir,
 * então o serviço lê File(filesDir, "ack-base.txt"). iOS usa a NSE (não lê este arquivo).
 */
const ARQUIVO_ACK_BASE = 'ack-base.txt';

/** Grava a URL base atual da API para o serviço nativo de entrega (Android). Best-effort. */
export async function sincronizarApiBaseNativo(): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    const base = API_URL.replace(/\/+$/, '');
    const destino = (FileSystem.documentDirectory ?? '') + ARQUIVO_ACK_BASE;
    await FileSystem.writeAsStringAsync(destino, base);
  } catch {
    // Se falhar, o serviço nativo usa o fallback compilado (produção) — não quebra nada.
  }
}
