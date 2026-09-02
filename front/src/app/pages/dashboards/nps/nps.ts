import { Component, afterNextRender, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { DashBars } from '../charts/dash-bars';
import { DashDonut } from '../charts/dash-donut';
import { DashLine } from '../charts/dash-line';
import { DashPeriodo } from '../charts/dash-periodo';
import { DashStat } from '../charts/dash-stat';
import { Fatia, NpsDashboard } from '../dashboard.models';
import { DashboardService } from '../dashboard.service';

@Component({
  selector: 'app-dashboard-nps',
  imports: [DashStat, DashLine, DashDonut, DashBars, DashPeriodo],
  templateUrl: './nps.html',
})
export class DashboardNps {
  private readonly service = inject(DashboardService);
  protected readonly unidade = inject(AuthService).unidadeNome;

  protected readonly dias = signal(30);
  protected readonly dados = signal<NpsDashboard | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);

  constructor() {
    afterNextRender(() => this.carregar(this.dias()));
  }

  protected mudarPeriodo(dias: number): void {
    this.dias.set(dias);
    this.carregar(dias);
  }

  protected recarregar(): void {
    this.carregar(this.dias());
  }

  /** Sequência de requisição: descarta respostas de períodos que o usuário já trocou. */
  private reqId = 0;

  private carregar(dias: number): void {
    this.carregando.set(true);
    this.erro.set(false);
    const req = ++this.reqId;
    this.service.nps(dias).subscribe({
      next: (d) => {
        if (req !== this.reqId) return;
        this.dados.set(d);
        this.carregando.set(false);
      },
      error: () => {
        if (req !== this.reqId) return;
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  protected delta(atual: number, anterior: number): number | null {
    if (!anterior) return null;
    return ((atual - anterior) / anterior) * 100;
  }

  protected media(v: number | null): string {
    return v === null || v === undefined ? '—' : v.toFixed(1);
  }

  protected categoriaItens(d: NpsDashboard): { rotulo: string; valor: number }[] {
    return d.mediaPorCategoria.map((c) => ({ rotulo: c.categoria, valor: c.media }));
  }

  protected satisfacaoFatias(d: NpsDashboard): Fatia[] {
    return [
      { chave: 'SAT', rotulo: 'Satisfeitos (4–5)', valor: d.satisfeitos },
      { chave: 'NEU', rotulo: 'Neutros (3)', valor: d.neutros },
      { chave: 'INS', rotulo: 'Insatisfeitos (1–2)', valor: d.insatisfeitos },
    ];
  }
}
