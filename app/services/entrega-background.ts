import { fetch } from 'expo/fetch';
import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

import { API_URL } from '@/constants/api';

/**
 * Entrega do chat (2º "check" ✓✓) com o app FECHADO no ANDROID.
 *
 * O backend manda, JUNTO da notificação visível, um push SILENCIOSO (data-only, alta prioridade)
 * com um token de entrega assinado em `data.ack`. O Android acorda esta TASK em background (mesmo
 * com o app encerrado — menos force-stop), que só faz um POST em /push/entregue confirmando a
 * entrega. Não mexe em UI nem segura WebSocket (a janela é curta). O iOS NÃO usa isto (tem a NSE).
 *
 * Precisa ser DEFINIDA em escopo de módulo e importada CEDO (topo do _layout) para a task já existir
 * quando o Android lançar o processo em background. Só define/registra no Android.
 */
const TAREFA_ENTREGA = 'ENTREGA_CHAT_BACKGROUND';

/**
 * Extrai o `ack` do payload da task. O Expo/Android entrega o `data` do push como STRING JSON
 * aninhada em `data.data.dataString` (e `data.data.body`); cobrimos também as outras formas
 * possíveis para robustez entre versões/plataformas.
 */
function extrairAck(data: unknown): string | null {
  const d = data as Record<string, any> | null | undefined;
  if (!d) return null;
  // Formas de OBJETO direto.
  for (const c of [d.ack, d?.data?.ack, d?.notification?.request?.content?.data?.ack]) {
    if (typeof c === 'string' && c) return c;
  }
  // Formas de STRING JSON (o caso real no Android: data.data.dataString / data.data.body).
  for (const s of [d?.data?.dataString, d?.data?.body, d.dataString, d.body, d?.notification?.data?.body]) {
    if (typeof s === 'string') {
      try {
        const j = JSON.parse(s);
        if (typeof j?.ack === 'string' && j.ack) return j.ack as string;
      } catch {
        // ignora JSON inválido
      }
    }
  }
  return null;
}

async function confirmarPorAck(ack: string): Promise<void> {
  try {
    // IMPORTANTE: usa o `fetch` do `expo/fetch` (nativo), NÃO o global do React Native.
    // O fetch do RN NÃO completa requisição HTTP quando o processo está em 2º plano/headless
    // (expo/expo#41146): a task disparava mas o POST só "saía" ao reabrir — era por isso que o
    // ✓✓ não aparecia com o app minimizado/fechado. O fetch do Expo usa a pilha de rede nativa
    // e conclui o POST no contexto headless. (Sem dependência nova: vem dentro do pacote `expo`.)
    await fetch(`${API_URL}/push/entregue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ack }),
    });
  } catch {
    // best-effort: se falhar, o 2º check ainda sai ao reabrir (rede de segurança).
  }
}

// Escopo de módulo: registra o CORPO da task antes de qualquer componente (requisito do TaskManager).
if (Platform.OS === 'android') {
  TaskManager.defineTask(TAREFA_ENTREGA, async ({ data, error }) => {
    if (error) return;
    const ack = extrairAck(data);
    if (ack) await confirmarPorAck(ack);
  });
}

/** Registra a task de entrega em background. Só Android — o iOS usa a Notification Service Extension. */
export async function registrarEntregaBackground(): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    await Notifications.registerTaskAsync(TAREFA_ENTREGA);
  } catch {
    // Sem dev build / não suportado: ignora (o 2º check ainda sai ao reabrir).
  }
}
