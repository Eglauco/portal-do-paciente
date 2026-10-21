import { Component, afterNextRender, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ModeloIa } from '../../shared/modelo-ia/modelo-ia.model';
import { ModeloIaService } from '../../shared/modelo-ia/modelo-ia.service';

/**
 * Lista dos Modelos de IA cadastrados (catálogo pequeno e fixo: carrega todos de uma vez, sem
 * paginação). Cada linha é a versão exata de um modelo Claude com o preço por MTok (entrada/saída).
 */
@Component({
  selector: 'app-modelos-ia-list',
  imports: [RouterLink],
  templateUrl: './modelos-ia-list.html',
})
export class ModelosIaList {
  private readonly service = inject(ModeloIaService);
  private readonly router = inject(Router);

  protected readonly modelos = signal<ModeloIa[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  constructor() {
    afterNextRender(() => this.carregar());
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(false);
    this.service.listar().subscribe({
      next: (modelos) => {
        this.modelos.set(modelos);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.modelos.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }

  protected editar(modelo: ModeloIa): void {
    this.router.navigate(['/modelos-ia', modelo.id]);
  }

  /** Formata o preço US$/MTok (ex.: 0.1 → "0,10"; 25 → "25,00"). */
  protected preco(valor: number): string {
    return (valor ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
  }
}
