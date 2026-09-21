import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { UsoIa, UsoIaTipo, UsoIaTotais } from './uso-ia.model';
import { UsoIaService } from './uso-ia.service';

export type PaginaItem = number | 'ellipsis';

/** Tela de auditoria (só consulta) do consumo de IA: uma linha por chamada, com totais e exportação. */
@Component({
  selector: 'app-uso-ia-list',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './uso-ia-list.html',
})
export class UsoIaList {
  private readonly service = inject(UsoIaService);
  private readonly toastr = inject(ToastrService);

  protected readonly tamanhos = UsoIaService.TAMANHOS;
  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);

  /** Frentes para o filtro (rótulos batem com o enum do backend). */
  protected readonly tipos: { valor: UsoIaTipo; rotulo: string }[] = [
    { valor: 'PRONTUARIO_DOCUMENTO', rotulo: 'Análise de documento' },
    { valor: 'PRONTUARIO_RESUMO', rotulo: 'Resumo do histórico' },
    { valor: 'CHAT_MENSAGEM', rotulo: 'Mensagem do chat' },
    { valor: 'MODERACAO_COMENTARIO', rotulo: 'Moderação de comentário' },
  ];

  protected readonly filtro = new FormGroup({
    tipo: new FormControl<string>('', { nonNullable: true }),
    de: new FormControl<string>('', { nonNullable: true }),
    ate: new FormControl<string>('', { nonNullable: true }),
    busca: new FormControl<string>('', { nonNullable: true }),
  });

  protected readonly size = signal(UsoIaService.TAMANHO_PADRAO);
  protected readonly registros = signal<UsoIa[]>([]);
  protected readonly totais = signal<UsoIaTotais | null>(null);
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
    this.filtro.reset({ tipo: '', de: '', ate: '', busca: '' });
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

  private filtroAtual() {
    const f = this.filtro.getRawValue();
    return {
      tipo: (f.tipo || null) as UsoIaTipo | null,
      de: f.de || null,
      ate: f.ate || null,
      busca: f.busca || null,
    };
  }

  private carregar(): void {
    const filtro = this.filtroAtual();
    this.loading.set(true);
    this.error.set(false);
    this.service.listar(filtro, this.page(), this.size()).subscribe({
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
    // Totais do MESMO filtro (independente da paginação).
    this.service.totais(filtro).subscribe({
      next: (t) => this.totais.set(t),
      error: () => this.totais.set(null),
    });
  }

  protected exportar(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    this.exportando.set(formato);
    this.service.exportar(formato, this.filtroAtual()).subscribe({
      next: (blob) => {
        this.baixar(blob, `uso-ia.${formato}`);
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
