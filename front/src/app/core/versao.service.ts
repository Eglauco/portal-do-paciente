import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { VERSAO_FRONT } from '../../environments/versao-front';

/** Versão de um componente: número (semver) + data do release em ISO (yyyy-MM-dd). */
export interface VersaoInfo {
  versao: string;
  /** Data do release em ISO yyyy-MM-dd (formatada para dd/MM/aaaa na UI). */
  data: string;
}

/**
 * Versões exibidas no admin. PRINCÍPIO: cada componente é dono só da SUA versão.
 * - FRONT: lido do próprio arquivo ({@link VERSAO_FRONT}), que entra no build → versão REAL no ar.
 * - BACK: via GET /versao (versão real do backend que está servindo).
 *
 * O admin NÃO mostra a versão do app: o back não a guarda e não há como saber a versão realmente
 * instalada em cada aparelho. A versão do app é vista no próprio app (tela Sobre).
 */
@Injectable({ providedIn: 'root' })
export class VersaoService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly base = `${environment.apiUrl}`;

  /** Versão do PRÓPRIO front (embutida no build). Sempre disponível. */
  readonly front: VersaoInfo = VERSAO_FRONT;
  /** Número da versão do front — usado no rótulo da sidebar e no login. */
  readonly versaoFront = VERSAO_FRONT.versao;

  private readonly backEstado = signal<VersaoInfo | null>(null);
  /** Versão do BACK (via /versao); null enquanto não carregou ou se o back estiver fora. */
  readonly back = this.backEstado.asReadonly();

  /**
   * Boot: busca a versão do back em GET /versao. FAIL-SOFT — em erro NÃO rejeita o initializer
   * (fica null e a UI usa o fallback). Só no navegador (igual TemaService/MarcaService): evita a
   * chamada no SSR e o descasamento de hidratação.
   */
  carregar(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.http.get<VersaoInfo>(`${this.base}/versao`).subscribe({
      next: (v) => this.backEstado.set(v),
      error: () => {
        /* back fora: mantém null; a UI exibe o fallback (não quebra, não trava o boot) */
      },
    });
  }

  /**
   * Converte uma data ISO (yyyy-MM-dd) para dd/MM/aaaa. Função PURA via split('-') —
   * NÃO usa `new Date('yyyy-MM-dd')` (que interpreta como UTC e desloca o dia por fuso).
   * Entrada vazia/malformada → '—'.
   */
  formatarData(iso: string | null | undefined): string {
    if (!iso) return '—';
    const partes = iso.split('-');
    if (partes.length !== 3) return '—';
    const [ano, mes, dia] = partes;
    if (!ano || !mes || !dia) return '—';
    return `${dia}/${mes}/${ano}`;
  }
}
