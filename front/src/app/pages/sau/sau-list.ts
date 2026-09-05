import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { RelatorioColunasModal } from '../../shared/relatorio-colunas-modal';
import { AuthService } from '../../core/auth.service';
import { TipoManifestacao } from '../tipos-manifestacao/tipo-manifestacao.model';
import { TipoManifestacaoService } from '../tipos-manifestacao/tipo-manifestacao.service';
import { Manifestacao, STATUS_OPTIONS, StatusManifestacao } from './sau.model';
import { SauBuscaStore } from './sau-busca.store';
import { SauService } from './sau.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-sau-list',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RelatorioColunasModal],
  templateUrl: './sau-list.html',
})
export class SauList {
  private readonly service = inject(SauService);
  private readonly tipoService = inject(TipoManifestacaoService);
  private readonly router = inject(Router);
  private readonly store = inject(SauBuscaStore);
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);
  /** Formato escolhido enquanto o modal de colunas está aberto (null = fechado). */
  protected readonly formatoModal = signal<'xlsx' | 'pdf' | null>(null);
  protected readonly base = this.service.base;

  protected readonly tamanhos = SauService.TAMANHOS;
  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly tipos = signal<TipoManifestacao[]>([]);

  protected readonly filtro = new FormGroup({
    tipoId: new FormControl<number | null>(this.store.tipoId),
    status: new FormControl<StatusManifestacao | null>(this.store.status),
  });

  protected readonly size = signal(this.store.size);
  protected readonly manifestacoes = signal<Manifestacao[]>([]);
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
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.manifestacoes().length);

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
    afterNextRender(() => {
      // Todos os tipos (ativos e inativos) para o filtro localizar manifestações antigas.
      this.tipoService.listar({}, 0, 100).subscribe({
        next: (p) => this.tipos.set(p.content),
        error: () => this.tipos.set([]),
      });
      this.carregar();
    });
  }

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.filtro.reset({ tipoId: null, status: null });
    this.store.limpar();
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
    const f = this.filtro.getRawValue();
    this.store.tipoId = f.tipoId;
    this.store.status = f.status;
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service
      .listar({ unidadeId: this.auth.unidadeId(), tipoId: f.tipoId, status: f.status }, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.manifestacoes.set(pagina.content);
          this.totalElements.set(pagina.totalElements);
          this.totalPages.set(pagina.totalPages);
          this.first.set(pagina.first);
          this.last.set(pagina.last);
          this.page.set(pagina.page);
          this.loading.set(false);
          this.carregado.set(true);
        },
        error: () => {
          this.manifestacoes.set([]);
          this.error.set(true);
          this.loading.set(false);
          this.carregado.set(true);
        },
      });
  }

  /** Abre o modal de seleção de colunas para o formato escolhido. */
  protected abrirExportacao(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    this.formatoModal.set(formato);
  }

  /** Exporta com as colunas escolhidas no modal (mesmos filtros da tela). */
  protected confirmarExportacao(colunas: string[]): void {
    const formato = this.formatoModal();
    this.formatoModal.set(null);
    if (!formato) return;
    const f = this.filtro.getRawValue();
    this.exportando.set(formato);
    this.service
      .exportar(formato, { unidadeId: this.auth.unidadeId(), tipoId: f.tipoId, status: f.status }, colunas)
      .subscribe({
        next: (blob) => {
          this.baixar(blob, `sau.${formato}`);
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

  protected abrir(m: Manifestacao): void {
    this.router.navigate(['/sau', m.id]);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }
}
