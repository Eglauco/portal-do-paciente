import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import { Chat, STATUS_OPTIONS, StatusChat, statusLabel } from './chat.model';
import { ChatBuscaStore } from './chat-busca.store';
import { ChatService } from './chat.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-chats-list',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe],
  templateUrl: './chats-list.html',
})
export class ChatsList {
  private readonly service = inject(ChatService);
  private readonly pacienteService = inject(PacienteService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly store = inject(ChatBuscaStore);

  protected readonly tamanhos = ChatService.TAMANHOS;
  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly rotuloStatus = statusLabel;

  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly unidades = signal<Unidade[]>([]);

  protected readonly filtro = new FormGroup({
    pacienteId: new FormControl<number | null>(this.store.pacienteId),
    unidadeId: new FormControl<number | null>(this.store.unidadeId),
    status: new FormControl<StatusChat | null>(this.store.status),
    naoResolvidas: new FormControl<boolean>(this.store.naoResolvidas, { nonNullable: true }),
  });

  protected readonly size = signal(this.store.size);
  protected readonly chats = signal<Chat[]>([]);
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
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.chats().length);

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
      this.pacienteService.listar({}, 0, 100).subscribe({ next: (p) => this.pacientes.set(p.content) });
      this.unidadeService.listar({}, 0, 100).subscribe({ next: (p) => this.unidades.set(p.content) });
      this.carregar();
    });
  }

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.filtro.reset({ pacienteId: null, unidadeId: null, status: null, naoResolvidas: false });
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
    this.store.pacienteId = f.pacienteId;
    this.store.unidadeId = f.unidadeId;
    this.store.status = f.status;
    this.store.naoResolvidas = f.naoResolvidas;
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service
      .listar(
        { pacienteId: f.pacienteId, unidadeId: f.unidadeId, status: f.status, naoResolvidas: f.naoResolvidas },
        this.page(),
        this.size(),
      )
      .subscribe({
        next: (pagina) => {
          this.chats.set(pagina.content);
          this.totalElements.set(pagina.totalElements);
          this.totalPages.set(pagina.totalPages);
          this.first.set(pagina.first);
          this.last.set(pagina.last);
          this.page.set(pagina.page);
          this.loading.set(false);
          this.carregado.set(true);
        },
        error: () => {
          this.chats.set([]);
          this.error.set(true);
          this.loading.set(false);
          this.carregado.set(true);
        },
      });
  }

  protected abrir(chat: Chat): void {
    this.router.navigate(['/chats', chat.id]);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }
}
