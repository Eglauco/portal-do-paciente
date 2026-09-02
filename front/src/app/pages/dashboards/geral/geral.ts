import { Component, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { DashDonut } from '../charts/dash-donut';
import { DashLine } from '../charts/dash-line';
import { DashPeriodo } from '../charts/dash-periodo';
import { DashStat } from '../charts/dash-stat';
import { GeralDashboard } from '../dashboard.models';
import { DashboardService } from '../dashboard.service';

@Component({
  selector: 'app-dashboard-geral',
  imports: [RouterLink, DashStat, DashLine, DashDonut, DashPeriodo],
  templateUrl: './geral.html',
})
export class DashboardGeral {
  private readonly service = inject(DashboardService);
  private readonly auth = inject(AuthService);

  protected readonly unidade = this.auth.unidadeNome;
  protected readonly dias = signal(30);
  protected readonly dados = signal<GeralDashboard | null>(null);
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
    this.service.geral(dias).subscribe({
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

  /** Variação percentual do período vs. o período anterior (null = sem base). */
  protected delta(atual: number, anterior: number): number | null {
    if (!anterior) return null;
    return ((atual - anterior) / anterior) * 100;
  }

  protected media(v: number | null): string {
    return v === null || v === undefined ? '—' : v.toFixed(1);
  }
}
