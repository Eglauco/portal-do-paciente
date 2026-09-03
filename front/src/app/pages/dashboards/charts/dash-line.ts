import { Component, computed, input, signal } from '@angular/core';
import { SerieDiaria } from '../dashboard.models';

interface Marca {
  xPct: number;
  yPct: number;
  data: string;
  valor: number;
}

/** Gráfico de área/linha para uma série diária (SVG puro, responsivo, SSR-safe).
 *  Eixo Y com rótulos e tooltip ao passar o mouse (mostra dia + valor). */
@Component({
  selector: 'dash-line',
  template: `
    @if (geo().n === 0) {
      <div class="chart__vazio">Sem dados no período.</div>
    } @else {
      <div class="linechart-wrap">
        <div class="linechart__yaxis" aria-hidden="true">
          <span>{{ geo().pico }}</span>
          <span>{{ geo().meio }}</span>
          <span>0</span>
        </div>
        <div class="linechart__plot" (mousemove)="aoMover($event)" (mouseleave)="hover.set(null)">
          <svg
            class="linechart"
            viewBox="0 0 300 120"
            preserveAspectRatio="none"
            role="img"
            [attr.aria-label]="'Série diária, total ' + geo().total + ', pico ' + geo().pico"
          >
            <line x1="0" y1="3" x2="300" y2="3" class="linechart__grid" />
            <line x1="0" y1="60" x2="300" y2="60" class="linechart__grid" />
            <line x1="0" y1="117" x2="300" y2="117" class="linechart__grid" />
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
          @if (hover(); as h) {
            <div class="linechart__guide" [style.left.%]="h.xPct"></div>
            <div class="linechart__dot" [style.left.%]="h.xPct" [style.top.%]="h.yPct" [style.background]="cor()"></div>
            <div
              class="linechart__tip"
              [class.tip--r]="h.xPct > 75"
              [class.tip--l]="h.xPct < 25"
              [style.left.%]="h.xPct"
            >
              <span class="linechart__tip-d">{{ h.data }}</span>
              <b>{{ h.valor }}</b>
            </div>
          }
        </div>
      </div>
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

  protected readonly hover = signal<Marca | null>(null);

  protected readonly geo = computed(() => {
    const pts = this.pontos();
    const W = 300;
    const H = 120;
    const n = pts.length;
    if (n === 0) {
      return { n, linha: '', area: '', total: 0, pico: 0, meio: 0, ini: '', fim: '', marcas: [] as Marca[] };
    }
    const valores = pts.map((p) => p.valor);
    const max = Math.max(1, ...valores);
    const topo = 3;
    const base = H - 3;
    const x = (i: number) => (n === 1 ? W / 2 : (i / (n - 1)) * W);
    const y = (v: number) => base - (v / max) * (base - topo);
    const coords = pts.map((p, i) => `${x(i).toFixed(1)},${y(p.valor).toFixed(1)}`);
    const linha = 'M' + coords.join(' L');
    const area = `${linha} L${W},${H} L0,${H} Z`;
    const marcas: Marca[] = pts.map((p, i) => ({
      xPct: (x(i) / W) * 100,
      yPct: (y(p.valor) / H) * 100,
      data: this.dm(p.data),
      valor: p.valor,
    }));
    return {
      n,
      linha,
      area,
      total: valores.reduce((a, b) => a + b, 0),
      pico: max,
      meio: Math.round(max / 2),
      ini: this.dm(pts[0].data),
      fim: this.dm(pts[n - 1].data),
      marcas,
    };
  });

  protected aoMover(evento: MouseEvent): void {
    const marcas = this.geo().marcas;
    if (marcas.length === 0) return;
    const rect = (evento.currentTarget as HTMLElement).getBoundingClientRect();
    if (rect.width === 0) return;
    const relX = ((evento.clientX - rect.left) / rect.width) * 100;
    let melhor = marcas[0];
    let dist = Math.abs(melhor.xPct - relX);
    for (const m of marcas) {
      const d = Math.abs(m.xPct - relX);
      if (d < dist) {
        dist = d;
        melhor = m;
      }
    }
    this.hover.set(melhor);
  }

  private dm(iso: string): string {
    return iso.length >= 10 ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : iso;
  }
}
