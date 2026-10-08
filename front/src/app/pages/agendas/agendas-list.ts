import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AgendaBuscaStore } from './agenda-busca.store';
import { AgendaImportModal } from './agenda-import-modal';
import { AgendasPorPaciente } from './agendas-por-paciente';
import { AgendaResumo } from './agenda.model';
import { AgendaService } from './agenda.service';

export type PaginaItem = number | 'ellipsis';

/** Lista de Agendas (slots) com filtros + botão "Nova agenda". Clica → detalhe (horários). */
@Component({
  selector: 'app-agendas-list',
  imports: [ReactiveFormsModule, DatePipe, RouterLink, AgendaImportModal, AgendasPorPaciente],
  templateUrl: './agendas-list.html',
  styles: [
    `
      .mode-toggle { display: inline-flex; border: 1px solid var(--line); border-radius: 0.6rem; overflow: hidden; margin: 0 0 1.25rem; }
      .mode-toggle button { border: 0; background: transparent; padding: 0.5rem 1.05rem; font-size: 0.9rem; font-weight: 600; color: var(--muted); cursor: pointer; display: inline-flex; align-items: center; gap: 0.45rem; }
      .mode-toggle button + button { border-left: 1px solid var(--line); }
      .mode-toggle button.is-active { background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--brand-deep, var(--brand)); }
      .mode-toggle svg { width: 1.05rem; height: 1.05rem; }
    `,
  ],
})
export class AgendasList {
  private readonly service = inject(AgendaService);
  private readonly auth = inject(AuthService);
  private readonly store = inject(AgendaBuscaStore);

  protected readonly tamanhos = AgendaService.TAMANHOS;

  protected readonly filtro = new FormGroup({
    data: new FormControl<string>(this.store.data, { nonNullable: true }),
    profissionalNome: new FormControl<string>(this.store.profissionalNome, { nonNullable: true }),
    especialidadeNome: new FormControl<string>(this.store.especialidadeNome, { nonNullable: true }),
  });

  /** Modo da tela: lista de agendas (slots) ou busca por paciente (marcações). Restaurado do último uso. */
  protected readonly modo = signal<'agenda' | 'paciente'>(this.store.modo);

  /** Controla a modal de importação de agenda por Excel (Fase 1 — só preview). */
  protected readonly importAberto = signal(false);

  protected readonly size = signal(this.store.size);
  protected readonly registros = signal<AgendaResumo[]>([]);
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
    // Só carrega a lista de agendas se a tela reabrir no modo "agenda" (no modo "paciente" o filho cuida de si).
    afterNextRender(() => {
      if (this.modo() === 'agenda') this.carregar();
    });
  }

  /** Alterna o modo e lembra no store; ao entrar em "agenda" pela 1ª vez, carrega a lista. */
  protected setModo(modo: 'agenda' | 'paciente'): void {
    this.modo.set(modo);
    this.store.modo = modo;
    if (modo === 'agenda' && !this.carregado()) this.carregar();
  }

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  /** Após confirmar a importação: fecha a modal e recarrega a lista (a nova agenda aparece). */
  protected aoImportar(): void {
    this.importAberto.set(false);
    this.buscar();
  }

  protected limpar(): void {
    this.filtro.reset({ data: '', profissionalNome: '', especialidadeNome: '' });
    this.store.data = '';
    this.store.profissionalNome = '';
    this.store.especialidadeNome = '';
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
    // Lembra o filtro/tamanho atuais para restaurar ao voltar à tela.
    this.store.data = v.data;
    this.store.profissionalNome = v.profissionalNome;
    this.store.especialidadeNome = v.especialidadeNome;
    this.store.size = this.size();
    this.store.page = this.page();
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
        this.store.page = pagina.page;
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
