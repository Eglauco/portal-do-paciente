import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Usuario } from './usuario.model';
import { UsuarioBuscaStore } from './usuario-busca.store';
import { UsuarioService } from './usuario.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-usuarios-list',
  imports: [RouterLink],
  templateUrl: './usuarios-list.html',
})
export class UsuariosList {
  private readonly service = inject(UsuarioService);
  private readonly router = inject(Router);
  private readonly store = inject(UsuarioBuscaStore);

  protected readonly tamanhos = UsuarioService.TAMANHOS;
  protected readonly size = signal(this.store.size);

  // Filtros — restaurados do último estado de busca.
  protected readonly codigo = signal(this.store.codigo);
  protected readonly nome = signal(this.store.nome);
  protected readonly email = signal(this.store.email);

  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  // Estado de paginação
  protected readonly page = signal(this.store.page);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  /** Índice do primeiro registro exibido (1-based). */
  protected readonly inicioFaixa = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );

  /** Índice do último registro exibido (1-based). */
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.usuarios().length);

  /** Números de página a exibir, com reticências quando há muitas páginas. */
  protected readonly paginasVisiveis = computed<PaginaItem[]>(() => {
    const total = this.totalPages();
    const atual = this.page();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i);
    }
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
    // Carrega apenas no browser (evita chamada de rede durante o SSR/pré-render).
    afterNextRender(() => this.carregar());
  }

  protected buscar(event?: Event): void {
    event?.preventDefault();
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.store.limpar();
    this.codigo.set('');
    this.nome.set('');
    this.email.set('');
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
    // Persiste o estado atual da busca para preservá-lo ao voltar para a tela.
    this.store.codigo = this.codigo();
    this.store.nome = this.nome();
    this.store.email = this.email();
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service
      .listar({ codigo: this.codigo(), nome: this.nome(), email: this.email() }, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.usuarios.set(pagina.content);
          this.totalElements.set(pagina.totalElements);
          this.totalPages.set(pagina.totalPages);
          this.first.set(pagina.first);
          this.last.set(pagina.last);
          this.page.set(pagina.page);
          this.loading.set(false);
          this.carregado.set(true);
        },
        error: () => {
          this.usuarios.set([]);
          this.error.set(true);
          this.loading.set(false);
          this.carregado.set(true);
        },
      });
  }

  protected editar(usuario: Usuario): void {
    this.router.navigate(['/usuarios', usuario.id]);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }

  protected updateCodigo(event: Event): void {
    this.codigo.set((event.target as HTMLInputElement).value);
  }

  protected updateNome(event: Event): void {
    this.nome.set((event.target as HTMLInputElement).value);
  }

  protected updateEmail(event: Event): void {
    this.email.set((event.target as HTMLInputElement).value);
  }
}
