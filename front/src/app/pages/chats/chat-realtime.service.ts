import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../../environments/environment';
import { Mensagem } from './chat.model';

interface Assinatura {
  destino: string;
  callback: (msg: IMessage) => void;
  sub?: StompSubscription;
}

/**
 * Conexão WebSocket/STOMP para o chat em tempo real.
 * Mantém um único cliente compartilhado e (re)inscreve nos tópicos ao conectar.
 */
@Injectable({ providedIn: 'root' })
export class ChatRealtimeService {
  private client: Client | null = null;
  private readonly assinaturas = new Map<string, Assinatura>();
  private seq = 0;

  private urlWs(): string {
    return environment.apiUrl.replace(/^http/, 'ws').replace(/\/+$/, '') + '/ws';
  }

  private garantirCliente(): Client {
    if (this.client) return this.client;
    this.client = new Client({
      brokerURL: this.urlWs(),
      reconnectDelay: 4000,
      onConnect: () => {
        // Ao conectar (inclusive reconexão), reinscreve tudo.
        this.assinaturas.forEach((a) => {
          a.sub = this.client!.subscribe(a.destino, a.callback);
        });
      },
    });
    this.client.activate();
    return this.client;
  }

  private inscrever(destino: string, callback: (msg: IMessage) => void): () => void {
    const cliente = this.garantirCliente();
    const chave = `s${this.seq++}`;
    const assinatura: Assinatura = { destino, callback };
    this.assinaturas.set(chave, assinatura);
    if (cliente.connected) {
      assinatura.sub = cliente.subscribe(destino, callback);
    }
    return () => {
      const a = this.assinaturas.get(chave);
      a?.sub?.unsubscribe();
      this.assinaturas.delete(chave);
      if (this.assinaturas.size === 0) {
        this.client?.deactivate();
        this.client = null;
      }
    };
  }

  /** Observa novas mensagens de uma conversa. Retorna a função de cancelamento. */
  observarMensagens(chatId: number, callback: (mensagem: Mensagem) => void): () => void {
    return this.inscrever(`/topic/chat/${chatId}`, (msg) => callback(JSON.parse(msg.body) as Mensagem));
  }

  /** Observa alterações na lista de conversas (qualquer nova mensagem). */
  observarLista(callback: () => void): () => void {
    return this.inscrever('/topic/chats', () => callback());
  }
}
