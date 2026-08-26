import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_URL } from '@/constants/api';
import { Mensagem, Remetente } from './chat';

export interface DigitandoEvento {
  de: Remetente;
}

interface Assinatura {
  destino: string;
  callback: (msg: IMessage) => void;
  sub?: StompSubscription;
}

let client: Client | null = null;
const assinaturas = new Map<string, Assinatura>();
let seq = 0;

function urlWs(): string {
  return API_URL.replace(/^http/, 'ws').replace(/\/+$/, '') + '/ws';
}

function garantirCliente(): Client {
  if (client) return client;
  client = new Client({
    brokerURL: urlWs(),
    reconnectDelay: 4000,
    // Correções necessárias no React Native: o RN altera o byte NULL que
    // termina cada frame STOMP, então forçamos frames binários no envio e
    // recompomos o NULL no recebimento.
    forceBinaryWSFrames: true,
    appendMissingNULLonIncoming: true,
    onConnect: () => {
      assinaturas.forEach((a) => {
        a.sub = client!.subscribe(a.destino, a.callback);
      });
    },
    onStompError: (frame) => console.warn('[STOMP] erro:', frame.headers['message']),
    onWebSocketError: () => console.warn('[STOMP] falha na conexão WebSocket:', urlWs()),
  });
  client.activate();
  return client;
}

function inscrever(destino: string, callback: (msg: IMessage) => void): () => void {
  const cliente = garantirCliente();
  const chave = `s${seq++}`;
  const assinatura: Assinatura = { destino, callback };
  assinaturas.set(chave, assinatura);
  if (cliente.connected) {
    assinatura.sub = cliente.subscribe(destino, callback);
  }
  return () => {
    const a = assinaturas.get(chave);
    a?.sub?.unsubscribe();
    assinaturas.delete(chave);
    if (assinaturas.size === 0) {
      client?.deactivate();
      client = null;
    }
  };
}

/** Observa novas mensagens de uma conversa. Retorna a função de cancelamento. */
export function observarMensagens(chatId: number | string, callback: (mensagem: Mensagem) => void): () => void {
  return inscrever(`/topic/chat/${chatId}`, (msg) => callback(JSON.parse(msg.body) as Mensagem));
}

/** Observa o evento "digitando…" de uma conversa. */
export function observarDigitando(chatId: number | string, callback: (e: DigitandoEvento) => void): () => void {
  return inscrever(`/topic/chat/${chatId}/digitando`, (msg) => callback(JSON.parse(msg.body) as DigitandoEvento));
}

/** Observa alterações na lista de conversas (qualquer nova mensagem). */
export function observarLista(callback: () => void): () => void {
  return inscrever('/topic/chats', () => callback());
}

/** Sinaliza que este lado está digitando (efêmero; ignora se não conectado). */
export function sinalizarDigitando(chatId: number | string, de: Remetente): void {
  const cliente = garantirCliente();
  if (cliente.connected) {
    cliente.publish({ destination: `/app/chat/${chatId}/digitando`, body: JSON.stringify({ de }) });
  }
}
