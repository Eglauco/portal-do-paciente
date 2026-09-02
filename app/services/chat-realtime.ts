import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_URL } from '@/constants/api';
import { Mensagem, Remetente } from './chat';
import { authHeaders } from './sessao';

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

/** Estado da conexão em tempo real (para a UI mostrar "ao vivo / conectando / offline"). */
export type EstadoConexao = 'conectando' | 'conectado' | 'offline';
let estado: EstadoConexao = 'offline';
let detalhe = ''; // motivo do último problema (diagnóstico): código de fecho, erro STOMP, etc.
const ouvintesEstado = new Set<(e: EstadoConexao, d: string) => void>();
function definirEstado(novo: EstadoConexao, motivo = ''): void {
  estado = novo;
  detalhe = motivo;
  ouvintesEstado.forEach((cb) => cb(novo, motivo));
}

/** Assina o estado da conexão em tempo real; devolve a função de cancelamento. */
export function observarConexao(callback: (estado: EstadoConexao, detalhe: string) => void): () => void {
  ouvintesEstado.add(callback);
  callback(estado, detalhe); // estado atual imediatamente
  return () => ouvintesEstado.delete(callback);
}

function urlWs(): string {
  return API_URL.replace(/^http/, 'ws').replace(/\/+$/, '') + '/ws';
}

function garantirCliente(): Client {
  if (client) return client;
  definirEstado('conectando');
  client = new Client({
    brokerURL: urlWs(),
    reconnectDelay: 4000,
    // Token do paciente no CONNECT (o backend autoriza a assinatura por conversa).
    connectHeaders: authHeaders(),
    beforeConnect: () => {
      definirEstado('conectando');
      if (client) client.connectHeaders = authHeaders();
    },
    // Correções necessárias no React Native: o RN altera o byte NULL que
    // termina cada frame STOMP, então forçamos frames binários no envio e
    // recompomos o NULL no recebimento.
    forceBinaryWSFrames: true,
    appendMissingNULLonIncoming: true,
    // Log do ciclo de vida (visível em `adb logcat` / dev) para diagnosticar a conexão.
    debug: (msg) => {
      if (msg) console.log('[STOMP]', msg);
    },
    onConnect: () => {
      definirEstado('conectado');
      console.log('[STOMP] conectado a', urlWs());
      assinaturas.forEach((a) => {
        a.sub = client!.subscribe(a.destino, a.callback);
      });
    },
    onStompError: (frame) => {
      definirEstado('offline', 'STOMP: ' + (frame.headers['message'] ?? 'erro'));
      console.warn('[STOMP] erro do broker:', frame.headers['message'], frame.body);
    },
    onWebSocketError: (evento) => {
      const msg = (evento as { message?: string })?.message ?? 'falha';
      definirEstado('offline', 'WS-erro: ' + msg);
      console.warn('[STOMP] falha no WebSocket:', msg, urlWs());
    },
    onWebSocketClose: (evento) => {
      definirEstado('offline', 'WS fechou ' + (evento?.code ?? '?') + (evento?.reason ? ' ' + evento.reason : ''));
      console.warn('[STOMP] WebSocket fechado:', evento?.code, evento?.reason);
    },
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

/** Observa a troca de atendente responsável (atualiza o nome no cabeçalho ao vivo). */
export function observarResponsavel(
  chatId: number | string,
  callback: (evento: { responsavelId: number | null; responsavelNome: string | null }) => void,
): () => void {
  return inscrever(`/topic/chat/${chatId}/responsavel`, (msg) =>
    callback(JSON.parse(msg.body) as { responsavelId: number | null; responsavelNome: string | null }),
  );
}

/** Sinaliza que este lado está digitando (efêmero; ignora se não conectado). */
export function sinalizarDigitando(chatId: number | string, de: Remetente): void {
  const cliente = garantirCliente();
  if (cliente.connected) {
    cliente.publish({ destination: `/app/chat/${chatId}/digitando`, body: JSON.stringify({ de }) });
  }
}
