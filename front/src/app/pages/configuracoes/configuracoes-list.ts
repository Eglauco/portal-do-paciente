import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Configuracao, TipoConfiguracao } from './configuracao.model';
import { ConfiguracaoBuscaStore } from './configuracao-busca.store';
import { ConfiguracaoService } from './configuracao.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-configuracoes-list',
  imports: [DatePipe],
  templateUrl: './configuracoes-list.html',
})
export class ConfiguracoesList {
  private readonly service = inject(ConfiguracaoService);
  private readonly router = inject(Router);
  private readonly store = inject(ConfiguracaoBuscaStore);

  protected readonly tamanhos = ConfiguracaoService.TAMANHOS;
  protected readonly size = signal(this.store.size);

  protected readonly busca = signal(this.store.busca);

  protected readonly configuracoes = signal<Configuracao[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  protected readonly page = signal(this.store.page);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  protected readonly inicioFaixa = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.configuracoes().length);

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

  protected buscar(event?: Event): void {
    event?.preventDefault();
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.store.limpar();
    this.busca.set('');
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
    this.store.busca = this.busca();
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service.listar({ busca: this.busca() }, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.configuracoes.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.configuracoes.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }

  protected editar(c: Configuracao): void {
    this.router.navigate(['/configuracoes', c.id]);
  }

  protected updateBusca(event: Event): void {
    this.busca.set((event.target as HTMLInputElement).value);
  }

  /** Rótulo amigável do tipo (coluna da tabela). */
  protected rotuloTipo(tipo: TipoConfiguracao): string {
    return tipo === 'BOOLEANO'
      ? 'Sim/Não'
      : tipo === 'NUMERICO'
        ? 'Numérico'
        : tipo === 'COR'
          ? 'Cor'
          : 'Texto';
  }

  /** Valor atual formatado para a tabela. */
  protected valorAtual(c: Configuracao): string {
    switch (c.tipoConfiguracao) {
      case 'BOOLEANO':
        return c.valorBooleano ? 'Sim' : 'Não';
      case 'NUMERICO':
        return c.valorNumerico == null ? '—' : String(c.valorNumerico);
      case 'TEXTO':
        return c.valorTexto && c.valorTexto.trim() ? c.valorTexto : '—';
      case 'COR':
        return c.valorCor ?? '—';
    }
  }
}
