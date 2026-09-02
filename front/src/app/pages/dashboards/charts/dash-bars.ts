import { Component, computed, input } from '@angular/core';

/** Ranking em barras horizontais (CSS). Serve para contagens e para médias (escalaMax=5). */
@Component({
  selector: 'dash-bars',
  template: `
    @if (itens().length === 0) {
      <div class="chart__vazio">Sem dados no período.</div>
    } @else {
      <ul class="bars">
        @for (it of itens(); track it.rotulo) {
          <li class="bars__row">
            <span class="bars__lbl" [title]="it.rotulo">{{ it.rotulo }}</span>
            <span class="bars__track">
              <span class="bars__fill" [style.width.%]="largura(it.valor)" [style.background]="cor()"></span>
            </span>
            <b class="bars__val">{{ rotuloValor(it.valor) }}</b>
          </li>
        }
      </ul>
    }
  `,
})
export class DashBars {
  readonly itens = input<{ rotulo: string; valor: number }[]>([]);
  /** Base do 100% da barra; ausente = maior valor da lista. Use 5 para médias 1–5. */
  readonly escalaMax = input<number | null>(null);
  readonly sufixo = input('');
  readonly decimais = input(0);
  readonly cor = input('#159a8a');

  protected readonly max = computed(() => {
    const explicito = this.escalaMax();
    if (explicito != null && explicito > 0) return explicito;
    return Math.max(1, ...this.itens().map((i) => i.valor));
  });

  protected largura(valor: number): number {
    return Math.max(0, Math.min(100, (valor / this.max()) * 100));
  }

  protected rotuloValor(valor: number): string {
    return valor.toFixed(this.decimais()) + this.sufixo();
  }
}
