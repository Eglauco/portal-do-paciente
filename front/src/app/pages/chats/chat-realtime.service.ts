import { Injectable, NgZone, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth.service';
import { Mensagem, Remetente } from './chat.model';

export interface DigitandoEvento {
  de: Remetente;
}

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
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);
  private client: Client | null = null;
  private readonly assinaturas = new Map<string, Assinatura>();
  private readonly aoConectar = new Set<() => void>();
  private seq = 0;

  private urlWs(): string {
    return environment.apiUrl.replace(/^http/, 'ws').replace(/\/+$/, '') + '/ws';
  }

  /**
   * Envolve o callback do STOMP na zona do Angular. Sem isso, atualizações de
   * signal feitas dentro do callback (ex.: confirmação de entrega) não disparam
   * a detecção de mudanças e a tela só atualiza na próxima interação.
   */
  private naZona(callback: (msg: IMessage) => void): (msg: IMessage) => void {
    return (msg) => this.zone.run(() => callback(msg));
  }

  private cabecalhos(): Record<string, string> {
    const token = this.auth.token();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private garantirCliente(): Client {
    if (this.client) return this.client;
    this.client = new Client({
      brokerURL: this.urlWs(),
      reconnectDelay: 4000,
      // Token do admin no CONNECT (o backend exige autenticação no WebSocket).
      connectHeaders: this.cabecalhos(),
      beforeConnect: () => {
        if (this.client) this.client.connectHeaders = this.cabecalhos();
      },
      onConnect: () => {
        // Ao conectar (inclusive reconexão), reinscreve tudo.
        this.assinaturas.forEach((a) => {
          a.sub = this.client!.subscribe(a.destino, this.naZona(a.callback));
        });
        // Avisa quem quer re-sincronizar o estado perdido na janela de conexão
        // (ex.: o 2º "check" da 1ª mensagem, cujo evento é único e não se repete).
        this.aoConectar.forEach((cb) => this.zone.run(cb));
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
      assinatura.sub = cliente.subscribe(destino, this.naZona(callback));
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

  /** Observa o evento "digitando…" da conversa. */
  observarDigitando(chatId: number, callback: (evento: DigitandoEvento) => void): () => void {
    return this.inscrever(`/topic/chat/${chatId}/digitando`, (msg) =>
      callback(JSON.parse(msg.body) as DigitandoEvento),
    );
  }

  /** Observa alterações na lista de conversas (qualquer nova mensagem). */
  observarLista(callback: () => void): () => void {
    return this.inscrever('/topic/chats', () => callback());
  }

  /** Observa a confirmação de entrega (o app do paciente recebeu as mensagens). */
  observarEntrega(chatId: number, callback: () => void): () => void {
    return this.inscrever(`/topic/chat/${chatId}/entregue`, () => callback());
  }

  /**
   * Notifica a cada (re)conexão do WebSocket. Útil para recarregar o retrato do
   * servidor e recuperar eventos únicos perdidos enquanto a conexão subia
   * (as inscrições só ficam ativas no onConnect).
   */
  observarConexao(callback: () => void): () => void {
    this.garantirCliente();
    this.aoConectar.add(callback);
    return () => this.aoConectar.delete(callback);
  }

  /** Sinaliza que este lado está digitando (efêmero; ignora se não conectado). */
  sinalizarDigitando(chatId: number, de: Remetente): void {
    const cliente = this.garantirCliente();
    if (cliente.connected) {
      cliente.publish({ destination: `/app/chat/${chatId}/digitando`, body: JSON.stringify({ de }) });
    }
  }
}
