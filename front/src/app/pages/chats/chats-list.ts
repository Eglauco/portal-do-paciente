import { DatePipe } from '@angular/common';
import { Component, DestroyRef, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { RelatorioColunasModal } from '../../shared/relatorio-colunas-modal';
import { AuthService } from '../../core/auth.service';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Usuario } from '../usuarios/usuario.model';
import { UsuarioService } from '../usuarios/usuario.service';
import { Chat, STATUS_OPTIONS, StatusChat, statusLabel } from './chat.model';
import { ChatBuscaStore } from './chat-busca.store';
import { ChatRealtimeService } from './chat-realtime.service';
import { ChatService } from './chat.service';

export type PaginaItem = number | 'ellipsis';

@Component({
  selector: 'app-chats-list',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink, RelatorioColunasModal],
  templateUrl: './chats-list.html',
})
export class ChatsList {
  private readonly service = inject(ChatService);
  private readonly pacienteService = inject(PacienteService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly router = inject(Router);
  private readonly store = inject(ChatBuscaStore);
  private readonly realtime = inject(ChatRealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);
  /** Formato escolhido enquanto o modal de colunas está aberto (null = fechado). */
  protected readonly formatoModal = signal<'xlsx' | 'pdf' | null>(null);
  protected readonly base = this.service.base;

  protected readonly tamanhos = ChatService.TAMANHOS;
  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly rotuloStatus = statusLabel;

  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly usuarios = signal<Usuario[]>([]);

  protected readonly filtro = new FormGroup({
    pacienteId: new FormControl<number | null>(this.store.pacienteId),
    responsavelId: new FormControl<number | null>(this.store.responsavelId),
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
      this.usuarioService.listar({}, 0, 100).subscribe({ next: (u) => this.usuarios.set(u.content) });
      this.carregar();
      // Recarrega a lista quando chega qualquer mensagem nova (tempo real).
      const cancelar = this.realtime.observarLista(() => this.carregar());
      this.destroyRef.onDestroy(cancelar);
    });
  }

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.filtro.reset({ pacienteId: null, responsavelId: null, status: null, naoResolvidas: false });
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
    this.store.responsavelId = f.responsavelId;
    this.store.status = f.status;
    this.store.naoResolvidas = f.naoResolvidas;
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service
      .listar(
        {
          pacienteId: f.pacienteId,
          unidadeId: this.auth.unidadeId(),
          responsavelId: f.responsavelId,
          status: f.status,
          naoResolvidas: f.naoResolvidas,
        },
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
    this.exportando.set(formato);
    const f = this.filtro.getRawValue();
    this.service
      .exportar(
        formato,
        {
          pacienteId: f.pacienteId,
          unidadeId: this.auth.unidadeId(),
          responsavelId: f.responsavelId,
          status: f.status,
          naoResolvidas: f.naoResolvidas,
        },
        colunas,
      )
      .subscribe({
        next: (blob) => {
          this.baixar(blob, `chats.${formato}`);
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
