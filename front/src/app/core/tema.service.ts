import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Paleta do tema (espelha PaletaTema no back). Cores hex, exceto brandRgb ("r, g, b"). */
export interface PaletaTema {
  brand: string;
  brandDeep: string;
  brandPine: string;
  glow: string;
  onBrand: string;
  brandRgb: string;
  /** Fundo da aplicação: tom bem claro da marca. */
  bg: string;
}

/**
 * Tema dinâmico: busca a cor primária (derivada) em GET /tema e injeta como CSS custom
 * properties no &lt;html&gt;. Os valores default em styles.css cobrem o SSR / o instante antes
 * de carregar; um cache em localStorage evita "flash" da cor em cargas seguintes.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly base = `${environment.apiUrl}/tema`;
  private static readonly CACHE = 'tema-paleta';

  /** Boot: aplica a paleta em cache (sem flash) e busca a atual do servidor. */
  aplicar(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const cache = this.lerCache();
    if (cache) this.pintar(cache);
    this.http.get<PaletaTema>(this.base).subscribe({
      next: (p) => {
        this.pintar(p);
        this.salvarCache(p);
      },
      error: () => {
        /* mantém os defaults do :root */
      },
    });
  }

  /** Prévia da paleta derivada de uma cor candidata (para o seletor de cor do admin). */
  preview(cor: string): Observable<PaletaTema> {
    return this.http.get<PaletaTema>(`${this.base}/preview`, { params: { cor } });
  }

  /** Escreve a paleta como CSS custom properties no <html> + atualiza o theme-color. */
  private pintar(p: PaletaTema): void {
    const root = document.documentElement.style;
    root.setProperty('--bg', p.bg);
    root.setProperty('--brand', p.brand);
    root.setProperty('--brand-deep', p.brandDeep);
    root.setProperty('--brand-pine', p.brandPine);
    root.setProperty('--glow', p.glow);
    root.setProperty('--on-brand', p.onBrand);
    root.setProperty('--brand-rgb', p.brandRgb);
    root.setProperty('--brand-deep-rgb', this.canais(p.brandDeep));
    root.setProperty('--brand-pine-rgb', this.canais(p.brandPine));
    root.setProperty('--glow-rgb', this.canais(p.glow));
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', p.brandDeep);
  }

  /** "#RRGGBB" → "r, g, b" (para rgba(var(--x-rgb), α)). */
  private canais(hex: string): string {
    const h = hex.replace('#', '');
    return `${parseInt(h.slice(0, 2), 16)}, ${parseInt(h.slice(2, 4), 16)}, ${parseInt(h.slice(4, 6), 16)}`;
  }

  private lerCache(): PaletaTema | null {
    try {
      const raw = localStorage.getItem(TemaService.CACHE);
      return raw ? (JSON.parse(raw) as PaletaTema) : null;
    } catch {
      return null;
    }
  }

  private salvarCache(p: PaletaTema): void {
    try {
      localStorage.setItem(TemaService.CACHE, JSON.stringify(p));
    } catch {
      /* localStorage indisponível: sem cache, sem problema */
    }
  }
}
