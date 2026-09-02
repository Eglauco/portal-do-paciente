import { Component, computed, input } from '@angular/core';
import { SerieDiaria } from '../dashboard.models';

/** Gráfico de área/linha para uma série diária (SVG puro, responsivo, SSR-safe). */
@Component({
  selector: 'dash-line',
  template: `
    @if (geo().n === 0) {
      <div class="chart__vazio">Sem dados no período.</div>
    } @else {
      <svg
        class="linechart"
        viewBox="0 0 300 80"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="'Série diária, total ' + geo().total + ', pico ' + geo().pico"
      >
        <line x1="0" y1="40" x2="300" y2="40" class="linechart__grid" />
        <path [attr.d]="geo().area" [attr.fill]="cor()" fill-opacity="0.12" />
        <path
          [attr.d]="geo().linha"
          fill="none"
          [attr.stroke]="cor()"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-linecap="round"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <div class="chart__foot">
        <span>{{ geo().ini }}</span>
        <span class="chart__foot-mid">Pico diário: {{ geo().pico }}</span>
        <span>{{ geo().fim }}</span>
      </div>
    }
  `,
})
export class DashLine {
  readonly pontos = input<SerieDiaria[]>([]);
  readonly cor = input('#159a8a');

  protected readonly geo = computed(() => {
    const pts = this.pontos();
    const W = 300;
    const H = 80;
    const n = pts.length;
    if (n === 0) return { n, linha: '', area: '', total: 0, pico: 0, ini: '', fim: '' };
    const valores = pts.map((p) => p.valor);
    const max = Math.max(1, ...valores);
    const x = (i: number) => (n === 1 ? W / 2 : (i / (n - 1)) * W);
    const y = (v: number) => H - 3 - (v / max) * (H - 6);
    const coords = pts.map((p, i) => `${x(i).toFixed(1)},${y(p.valor).toFixed(1)}`);
    const linha = 'M' + coords.join(' L');
    const area = `${linha} L${W},${H} L0,${H} Z`;
    return {
      n,
      linha,
      area,
      total: valores.reduce((a, b) => a + b, 0),
      pico: max,
      ini: this.dm(pts[0].data),
      fim: this.dm(pts[n - 1].data),
    };
  });

  private dm(iso: string): string {
    return iso.length >= 10 ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : iso;
  }
}
