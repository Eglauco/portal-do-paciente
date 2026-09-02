import { Component, computed, input } from '@angular/core';

/** Cartão de KPI: rótulo, valor grande, sub-texto opcional e delta (%) opcional. */
@Component({
  selector: 'dash-stat',
  template: `
    <article class="stat stat--{{ tom() }}">
      <div class="stat__head">
        <span class="stat__label">{{ rotulo() }}</span>
        @if (temDelta()) {
          <span
            class="stat__delta"
            [class.stat__delta--up]="(delta() ?? 0) >= 0"
            [class.stat__delta--down]="(delta() ?? 0) < 0"
            [attr.aria-label]="deltaAria()"
            >{{ deltaLabel() }}</span
          >
        }
      </div>
      <p class="stat__value">{{ valor() }}</p>
      @if (sub()) {
        <p class="stat__sub">{{ sub() }}</p>
      }
    </article>
  `,
})
export class DashStat {
  readonly rotulo = input('');
  readonly valor = input<string | number>('');
  readonly sub = input<string | null>(null);
  /** Variação percentual vs. período anterior (opcional). */
  readonly delta = input<number | null>(null);
  readonly tom = input<'teal' | 'blue' | 'amber' | 'red' | 'green' | 'violet' | 'slate'>('teal');

  protected readonly temDelta = computed(() => this.delta() !== null && this.delta() !== undefined);
  protected readonly deltaLabel = computed(() => {
    const d = this.delta();
    if (d === null || d === undefined) return '';
    return `${d >= 0 ? '▲' : '▼'} ${Math.abs(d).toFixed(1)}%`;
  });
  /** Rótulo acessível: a direção não pode depender só do glifo ▲/▼. */
  protected readonly deltaAria = computed(() => {
    const d = this.delta();
    if (d === null || d === undefined) return null;
    return `${d >= 0 ? 'aumento' : 'queda'} de ${Math.abs(d).toFixed(1)}% vs. período anterior`;
  });
}
