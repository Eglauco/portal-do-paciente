import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, afterNextRender, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { NpsDetalhe, statusLabel } from './nps.model';
import { NpsService } from './nps.service';

@Component({
  selector: 'app-nps-detalhe',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './nps-detalhe.html',
})
export class NpsDetalheComponent {
  private readonly service = inject(NpsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly rotuloStatus = statusLabel;

  protected readonly detalhe = signal<NpsDetalhe | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly processando = signal(false);

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    afterNextRender(() => {
      if (idParam) this.carregar(Number(idParam));
    });
  }

  private carregar(id: number): void {
    this.loading.set(true);
    this.error.set(false);
    this.service.detalhe(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected expirar(): void {
    const atual = this.detalhe();
    if (!atual || this.processando()) return;
    this.processando.set(true);
    this.service.expirar(atual.id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.processando.set(false);
        this.toastr.success('Avaliação marcada como expirada.');
      },
      error: () => {
        this.processando.set(false);
        this.toastr.error('Não foi possível atualizar a avaliação.');
      },
    });
  }

  protected voltar(): void {
    this.router.navigate(['/nps']);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }
}
