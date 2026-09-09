import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

/** Tipo do evento (espelha TipoNotificacaoAdmin no back) — define o ícone no sino. */
export type TipoNotificacaoAdmin = 'SAU' | 'MODERACAO' | 'NPS';

export interface NotificacaoAdmin {
  id: number;
  tipo: TipoNotificacaoAdmin;
  titulo: string;
  corpo: string;
  /** Rota do front para onde o clique leva (ex.: '/sau/123'). */
  rota: string;
  referenciaId: number | null;
  lida: boolean;
  criadoEm: string;
}

interface PaginaNotificacoes {
  content: NotificacaoAdmin[];
  last: boolean;
  totalElements: number;
}

/**
 * Sino de notificações do back-office (admin). Só contagem + lista + marcar lida(s).
 * Escopo (admin logado + unidade ativa) é resolvido no servidor pelo token. Erros são
 * engolidos: o sino nunca deve atrapalhar a navegação.
 */
@Injectable({ providedIn: 'root' })
export class NotificacaoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/notificacoes`;

  private readonly _naoLidas = signal(0);
  private readonly _itens = signal<NotificacaoAdmin[]>([]);
  /** Contagem para o badge do sino. */
  readonly naoLidas = this._naoLidas.asReadonly();
  /** Notificações carregadas (mais recentes primeiro). */
  readonly itens = this._itens.asReadonly();

  /** Atualiza só a contagem do sino (usado no polling). */
  atualizarContagem(): void {
    this.http.get<{ total: number }>(`${this.base}/nao-lidas`).subscribe({
      next: (r) => this._naoLidas.set(r.total ?? 0),
      error: () => {},
    });
  }

  /** Carrega a lista (ao abrir o sino) e sincroniza a contagem. */
  carregar(): void {
    this.http.get<PaginaNotificacoes>(`${this.base}?page=0&size=30`).subscribe({
      next: (p) => this._itens.set(Array.isArray(p.content) ? p.content : []),
      error: () => {},
    });
    this.atualizarContagem();
  }

  /** Marca uma como lida (otimista) e navega é feito pelo chamador. */
  marcarLida(n: NotificacaoAdmin): void {
    if (!n.lida) {
      this._itens.update((l) => l.map((x) => (x.id === n.id ? { ...x, lida: true } : x)));
      this._naoLidas.update((c) => Math.max(0, c - 1));
      this.http.post<void>(`${this.base}/${n.id}/lida`, {}).subscribe({ error: () => {} });
    }
  }

  /** Marca todas como lidas (botão "marcar todas como lidas"). */
  marcarTodasLidas(): void {
    this._itens.update((l) => l.map((x) => ({ ...x, lida: true })));
    this._naoLidas.set(0);
    this.http.post<void>(`${this.base}/marcar-todas-lidas`, {}).subscribe({ error: () => {} });
  }

  /** Zera o estado (no logout) para o próximo admin na mesma aba não ver dados do anterior. */
  limpar(): void {
    this._itens.set([]);
    this._naoLidas.set(0);
  }
}
