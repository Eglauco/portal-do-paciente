import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AgendaResumo } from './agenda.model';
import { AgendaService } from './agenda.service';

export type PaginaItem = number | 'ellipsis';

/** Lista de Agendas (slots) com filtros + botão "Nova agenda". Clica → detalhe (horários). */
@Component({
  selector: 'app-agendas-list',
  imports: [ReactiveFormsModule, DatePipe, RouterLink],
  templateUrl: './agendas-list.html',
})
export class AgendasList {
  private readonly service = inject(AgendaService);
  private readonly auth = inject(AuthService);

  protected readonly tamanhos = AgendaService.TAMANHOS;

  protected readonly filtro = new FormGroup({
    data: new FormControl<string>('', { nonNullable: true }),
    profissionalNome: new FormControl<string>('', { nonNullable: true }),
    especialidadeNome: new FormControl<string>('', { nonNullable: true }),
  });

  protected readonly size = signal(AgendaService.TAMANHO_PADRAO);
  protected readonly registros = signal<AgendaResumo[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  protected readonly inicioFaixa = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.registros().length);

  protected readonly paginasVisiveis = computed<PaginaItem[]>(() => {
    const total = this.totalPages();
    const atual = this.page();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const itens: PaginaItem[] = [0];
    const inicio = Math.max(1, atual - 1);
    const fim = Math.min(total - 2, atual + 1);
    if (inicio > 1) itens.push('ellipsis');
    for (let i = inicio; i <= fim; i++) itens.push(i);
    if (fim < total - 2) itens.push('ellipsis');
    itens.push(total - 1);
    return itens;
  });

  constructor() {
    afterNextRender(() => this.carregar());
  }

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.filtro.reset({ data: '', profissionalNome: '', especialidadeNome: '' });
    this.page.set(0);
    this.carregar();
  }

  protected alterarTamanho(event: Event): void {
    this.size.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
    this.carregar();
  }

  protected irParaPagina(indice: number): void {
    if (indice < 0 || indice >= this.totalPages() || indice === this.page()) return;
    this.page.set(indice);
    this.carregar();
  }

  protected paginaAnterior(): void {
    if (!this.first()) this.irParaPagina(this.page() - 1);
  }

  protected proximaPagina(): void {
    if (!this.last()) this.irParaPagina(this.page() + 1);
  }

  private carregar(): void {
    const v = this.filtro.getRawValue();
    const filtro = {
      data: v.data || null,
      profissionalNome: v.profissionalNome || null,
      especialidadeNome: v.especialidadeNome || null,
    };
    this.loading.set(true);
    this.error.set(false);
    this.service.listar(filtro, this.auth.unidadeId(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.registros.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.registros.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }
}
