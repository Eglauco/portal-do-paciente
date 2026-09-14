import { Component, computed, input, output } from '@angular/core';
import { Ordenacao } from './ordenacao.model';

/**
 * Cabeçalho de tabela ordenável. Use num <th>:
 *   <th appOrdenavel="nome" [ordenacoes]="ordenacoes()" (alternar)="aoAlternar($event)">Nome</th>
 *
 * O <th> mantém o aria-sort (papel columnheader) e a ação fica num <button> interno
 * (padrão WAI-ARIA APG: control operável, foco e teclado nativos). Mostra a seta (↑/↓)
 * e, na combinação, o número de prioridade — com um texto sr-only para leitores de tela,
 * já que o aria-sort não expressa a ordem. O clique emite {campo, combinar}; combinar=true
 * quando o Shift está pressionado (funciona no mouse e no teclado, pois Enter/Espaço num
 * botão disparam um click que carrega o estado do Shift).
 */
@Component({
  selector: 'th[appOrdenavel]',
  template: `
    <button
      type="button"
      class="ordenavel__btn"
      title="Clique para ordenar · Shift+clique para combinar"
      (click)="aoClicar($event)"
    >
      <span class="ordenavel__rotulo"><ng-content /></span
      ><span class="ordenavel__ind" aria-hidden="true">
        @if (direcao() === 'asc') {
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 14 6-6 6 6" /></svg>
        } @else if (direcao() === 'desc') {
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 10 6 6 6-6" /></svg>
        } @else {
          <svg class="ordenavel__fraca" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m8 9 4-4 4 4M8 15l4 4 4-4" /></svg>
        }
        @if (mostraBadge()) {
          <span class="ordenavel__num">{{ indice() + 1 }}</span>
        }
      </span>
      @if (mostraBadge()) {
        <span class="ordenavel__sr">, ordem {{ indice() + 1 }}</span>
      }
    </button>
  `,
  styles: [`
    .ordenavel__btn {
      display: inline-flex;
      align-items: center;
      gap: 0.15rem;
      width: 100%;
      font: inherit;
      color: inherit;
      text-transform: inherit;
      letter-spacing: inherit;
      text-align: left;
      background: none;
      border: none;
      margin: 0;
      padding: 0;
      cursor: pointer;
      user-select: none;
    }
    .ordenavel__btn:hover { color: var(--brand-deep); }
    .ordenavel__btn:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; border-radius: 6px; }
    .ordenavel__rotulo { vertical-align: middle; }
    .ordenavel__ind { display: inline-flex; align-items: center; gap: 0.15rem; margin-left: 0.35rem; }
    .ordenavel__ind svg { width: 0.95rem; height: 0.95rem; stroke: var(--brand-deep); }
    .ordenavel__fraca { opacity: 0; transition: opacity 0.12s ease; stroke: var(--muted) !important; }
    .ordenavel__btn:hover .ordenavel__fraca { opacity: 0.55; }
    .ordenavel__num {
      display: inline-grid;
      place-items: center;
      min-width: 1.05rem;
      height: 1.05rem;
      padding: 0 0.2rem;
      font-size: 0.66rem;
      font-weight: 800;
      line-height: 1;
      color: #fff;
      background: var(--brand-deep);
      border-radius: 999px;
    }
    .ordenavel__sr {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
  `],
  host: {
    class: 'ordenavel',
    '[attr.aria-sort]': 'ariaSort()',
  },
})
export class Ordenavel {
  readonly campo = input.required<string>({ alias: 'appOrdenavel' });
  readonly ordenacoes = input<readonly Ordenacao[]>([]);
  readonly alternar = output<{ campo: string; combinar: boolean }>();

  protected readonly direcao = computed<DirecaoOuNula>(
    () => this.ordenacoes().find((o) => o.campo === this.campo())?.direcao ?? null,
  );
  protected readonly indice = computed(() => this.ordenacoes().findIndex((o) => o.campo === this.campo()));
  protected readonly mostraBadge = computed(() => this.ordenacoes().length > 1 && this.indice() >= 0);
  protected readonly ariaSort = computed(() => {
    const d = this.direcao();
    return d === 'asc' ? 'ascending' : d === 'desc' ? 'descending' : 'none';
  });

  protected aoClicar(evento: MouseEvent): void {
    this.alternar.emit({ campo: this.campo(), combinar: evento.shiftKey });
  }
}

type DirecaoOuNula = Ordenacao['direcao'] | null;
