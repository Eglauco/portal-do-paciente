import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import { Postagem } from './postagem.model';
import { PostagemBuscaStore } from './postagem-busca.store';
import { PostagemService } from './postagem.service';

export type PaginaItem = number | 'ellipsis';

const COMENTARIOS_OPCOES = [
  { value: true, label: 'Habilitados' },
  { value: false, label: 'Desabilitados' },
];

@Component({
  selector: 'app-postagens-list',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink],
  templateUrl: './postagens-list.html',
})
export class PostagensList {
  private readonly service = inject(PostagemService);
  private readonly router = inject(Router);
  private readonly store = inject(PostagemBuscaStore);
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);

  protected readonly tamanhos = PostagemService.TAMANHOS;
  protected readonly comentariosOpcoes = COMENTARIOS_OPCOES;

  protected readonly filtro = new FormGroup({
    titulo: new FormControl<string>(this.store.titulo, { nonNullable: true }),
    comentarios: new FormControl<boolean | null>(this.store.comentarios),
  });

  protected readonly size = signal(this.store.size);
  protected readonly registros = signal<Postagem[]>([]);
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
    this.filtro.reset({ titulo: '', comentarios: null });
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
    this.store.titulo = f.titulo;
    this.store.comentarios = f.comentarios;
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service
      .listar({ titulo: f.titulo, unidadeId: this.auth.unidadeId(), comentarios: f.comentarios }, this.page(), this.size())
      .subscribe({
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

  protected editar(p: Postagem): void {
    this.router.navigate(['/postagens', p.id]);
  }

  /** Exporta as postagens dos filtros atuais (mesmos da tela) em Excel ou PDF. */
  protected exportar(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    this.exportando.set(formato);
    const f = this.filtro.getRawValue();
    this.service
      .exportar(formato, { titulo: f.titulo, unidadeId: this.auth.unidadeId(), comentarios: f.comentarios })
      .subscribe({
        next: (blob) => {
          this.baixar(blob, `postagens.${formato}`);
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
}
