import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { TipoManifestacao } from './tipo-manifestacao.model';
import { TipoManifestacaoBuscaStore } from './tipo-manifestacao-busca.store';
import { TipoManifestacaoService } from './tipo-manifestacao.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-tipos-manifestacao-list',
  imports: [RouterLink],
  templateUrl: './tipos-manifestacao-list.html',
})
export class TiposManifestacaoList {
  private readonly service = inject(TipoManifestacaoService);
  private readonly router = inject(Router);
  private readonly store = inject(TipoManifestacaoBuscaStore);
  private readonly toastr = inject(ToastrService);

  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);

  protected readonly tamanhos = TipoManifestacaoService.TAMANHOS;
  protected readonly size = signal(this.store.size);

  protected readonly nome = signal(this.store.nome);
  /** '' = todos, 'true' = ativos, 'false' = inativos (valor do <select>). */
  protected readonly situacao = signal(this.store.ativo == null ? '' : String(this.store.ativo));

  protected readonly tipos = signal<TipoManifestacao[]>([]);
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
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.tipos().length);

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
    this.nome.set('');
    this.situacao.set('');
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
    const ativo = this.situacao() === '' ? null : this.situacao() === 'true';
    this.store.nome = this.nome();
    this.store.ativo = ativo;
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service.listar({ nome: this.nome(), ativo }, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.tipos.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.tipos.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }

  protected editar(tipo: TipoManifestacao): void {
    this.router.navigate(['/tipos-manifestacao', tipo.id]);
  }

  /** Exporta os tipos dos filtros atuais (mesmos da tela) em Excel ou PDF. */
  protected exportar(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    const ativo = this.situacao() === '' ? null : this.situacao() === 'true';
    this.exportando.set(formato);
    this.service.exportar(formato, { nome: this.nome(), ativo }).subscribe({
      next: (blob) => {
        this.baixar(blob, `tipos-manifestacao.${formato}`);
        this.exportando.set(null);
      },
      error: () => {
        this.exportando.set(null);
        this.toastr.error('Não foi possível exportar os dados.');
      },
    });
  }

  private baixar(blob: Blob, nome: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
    URL.revokeObjectURL(url);
  }

  protected updateNome(event: Event): void {
    this.nome.set((event.target as HTMLInputElement).value);
  }

  protected updateSituacao(event: Event): void {
    this.situacao.set((event.target as HTMLSelectElement).value);
    this.buscar();
  }
}
