import { Component, afterNextRender, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { DashBars } from '../charts/dash-bars';
import { DashDonut } from '../charts/dash-donut';
import { DashLine } from '../charts/dash-line';
import { DashPeriodo } from '../charts/dash-periodo';
import { DashStat } from '../charts/dash-stat';
import { SauDashboard } from '../dashboard.models';
import { DashboardService } from '../dashboard.service';

@Component({
  selector: 'app-dashboard-sau',
  imports: [DashStat, DashLine, DashDonut, DashBars, DashPeriodo],
  templateUrl: './sau.html',
})
export class DashboardSau {
  private readonly service = inject(DashboardService);
  protected readonly unidade = inject(AuthService).unidadeNome;

  protected readonly dias = signal(30);
  protected readonly dados = signal<SauDashboard | null>(null);
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
    this.service.sau(dias).subscribe({
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

  /** Média formatada (1 casa); "—" quando não houve avaliação no período. */
  protected media(valor: number, avaliacoes: number): string {
    return avaliacoes > 0 ? valor.toFixed(1) : '—';
  }

  protected autorItens(d: SauDashboard): { rotulo: string; valor: number }[] {
    return [
      { rotulo: 'Paciente', valor: d.mensagensPaciente },
      { rotulo: 'SAU', valor: d.mensagensSau },
    ];
  }
}
