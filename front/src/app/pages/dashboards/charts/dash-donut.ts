import { Component, computed, input } from '@angular/core';
import { Fatia } from '../dashboard.models';
import { cor } from './palette';

/** Rosca (donut) de distribuição por categoria/status, com legenda de valores e %. */
@Component({
  selector: 'dash-donut',
  template: `
    @if (geo().total === 0) {
      <div class="chart__vazio">Sem dados no período.</div>
    } @else {
      <div class="donut">
        <svg class="donut__svg" viewBox="0 0 42 42" role="img" [attr.aria-label]="'Distribuição, total ' + geo().total">
          <circle class="donut__track" cx="21" cy="21" r="15.915" fill="none" stroke-width="5" />
          @for (s of geo().slices; track s.rotulo) {
            @if (s.valor > 0) {
              <circle
                cx="21"
                cy="21"
                r="15.915"
                fill="none"
                [attr.stroke]="s.corHex"
                stroke-width="5"
                [attr.stroke-dasharray]="s.dasharray"
                [attr.stroke-dashoffset]="s.dashoffset"
              >
                <title>{{ s.rotulo }}: {{ s.valor }} ({{ s.pctLabel }})</title>
              </circle>
            }
          }
          <text x="21" y="20.5" text-anchor="middle" class="donut__total">{{ geo().total }}</text>
          <text x="21" y="25.5" text-anchor="middle" class="donut__cap">total</text>
        </svg>
        <ul class="donut__legend">
          @for (s of geo().slices; track s.rotulo) {
            <li>
              <span class="dot" [style.background]="s.corHex"></span>
              <span class="donut__leg-lbl">{{ s.rotulo }}</span>
              <b>{{ s.valor }}</b>
              <span class="donut__leg-pct">{{ s.pctLabel }}</span>
            </li>
          }
        </ul>
      </div>
    }
  `,
})
export class DashDonut {
  readonly fatias = input<Fatia[]>([]);

  protected readonly geo = computed(() => {
    const fatias = this.fatias();
    const total = fatias.reduce((a, f) => a + f.valor, 0);
    let acc = 0;
    const slices = fatias.map((f, i) => {
      const pct = total > 0 ? (f.valor / total) * 100 : 0;
      const slice = {
        rotulo: f.rotulo,
        valor: f.valor,
        corHex: cor(i),
        dasharray: `${pct.toFixed(2)} ${(100 - pct).toFixed(2)}`,
        dashoffset: (125 - acc).toFixed(2),
        pctLabel: `${(Math.round(pct * 10) / 10).toFixed(1)}%`,
      };
      acc += pct;
      return slice;
    });
    return { total, slices };
  });
}
