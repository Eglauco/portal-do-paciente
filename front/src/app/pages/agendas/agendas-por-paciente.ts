import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { PacienteSelecao } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Horario, StatusAgendamento } from './agenda.model';
import { AgendaService } from './agenda.service';

export type PaginaItem = number | 'ellipsis';

/** Classe de cor do badge por status (amber/azul/verde/vermelho/cinza). */
const CLASSE_STATUS: Record<StatusAgendamento, string> = {
  AGUARDANDO_CONFIRMACAO_PACIENTE: 'st--aguardando',
  PACIENTE_CONFIRMOU: 'st--confirmou',
  PRESENCA_PACIENTE: 'st--presenca',
  FALTA_PACIENTE: 'st--falta',
  CANCELADO_PELA_UNIDADE: 'st--cancelado',
  CANCELADO_PELO_PACIENTE: 'st--cancelado',
};

/**
 * Visão "por paciente" da tela de Agendamentos: um typeahead (nome/CPF/prontuário) seleciona o paciente e
 * mostra as marcações dele numa linha do tempo (data/hora, profissional, especialidade, status), escopadas
 * pela unidade ativa. Cada marcação abre o horário (/horarios/:id).
 */
@Component({
  selector: 'app-agendas-por-paciente',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink],
  templateUrl: './agendas-por-paciente.html',
  styles: [
    `
      .pp__search { margin: 0 0 1.25rem; max-width: 560px; }
      .pp__search .label { display: block; font-size: 0.85rem; color: var(--muted); margin-bottom: 0.35rem; }
      .opt__nome { font-size: 0.95rem; }
      .opt__meta { font-size: 0.78rem; color: var(--muted); }

      .pac-head { display: flex; align-items: center; gap: 0.9rem; padding: 0.9rem 1rem; border: 1px solid var(--line); border-radius: 0.7rem; margin-bottom: 1rem; }
      .pac-head__av { width: 44px; height: 44px; border-radius: 50%; flex: none; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 0.95rem; background: color-mix(in srgb, var(--brand) 14%, transparent); color: var(--brand-deep, var(--brand)); }
      .pac-head__nome { font-weight: 700; font-size: 1.05rem; }
      .pac-head__meta { font-size: 0.83rem; color: var(--muted); margin-top: 0.1rem; }
      .pac-head__count { margin-left: auto; font-size: 0.85rem; color: var(--muted); white-space: nowrap; }

      .marc-list { display: flex; flex-direction: column; border: 1px solid var(--line); border-radius: 0.7rem; overflow: hidden; }
      .marc { display: flex; align-items: center; gap: 1rem; padding: 0.75rem 0.9rem; border-bottom: 1px solid var(--line); text-decoration: none; color: inherit; transition: background 0.12s; }
      .marc:last-child { border-bottom: 0; }
      .marc:hover { background: color-mix(in srgb, var(--brand) 5%, transparent); }
      .marc__data { width: 128px; flex: none; }
      .marc__dia { font-weight: 600; font-size: 0.92rem; }
      .marc__hora { font-size: 0.82rem; color: var(--muted); display: inline-flex; align-items: center; gap: 0.3rem; margin-top: 0.1rem; }
      .marc__hora svg { width: 0.9rem; height: 0.9rem; }
      .marc__quem { flex: 1; min-width: 0; }
      .marc__quem .prof { font-size: 0.92rem; }
      .marc__quem .esp { font-size: 0.82rem; color: var(--muted); margin-top: 0.1rem; }
      .marc__go { width: 1.1rem; height: 1.1rem; color: var(--muted); flex: none; }

      .st { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.2rem 0.65rem; border-radius: 999px; font-size: 0.77rem; font-weight: 600; white-space: nowrap; flex: none; }
      .st::before { content: ''; width: 0.5rem; height: 0.5rem; border-radius: 50%; background: currentColor; flex: none; }
      .st--aguardando { color: #9a6a00; background: color-mix(in srgb, #e6a700 20%, transparent); }
      .st--confirmou { color: var(--brand-deep, #0a5bd3); background: color-mix(in srgb, var(--brand) 14%, transparent); }
      .st--presenca { color: #0a7a4b; background: color-mix(in srgb, #12b76a 16%, transparent); }
      .st--falta { color: #b42318; background: color-mix(in srgb, #f04438 15%, transparent); }
      .st--cancelado { color: var(--muted); background: color-mix(in srgb, var(--muted) 16%, transparent); }
    `,
  ],
})
export class AgendasPorPaciente {
  private readonly pacienteService = inject(PacienteService);
  private readonly service = inject(AgendaService);
  private readonly auth = inject(AuthService);

  protected readonly pacienteCtrl = new FormControl<PacienteSelecao | null>(null);
  protected readonly termo$ = new Subject<string>();
  protected readonly sugestoes = signal<PacienteSelecao[]>([]);
  protected readonly buscando = signal(false);

  protected readonly paciente = signal<PacienteSelecao | null>(null);

  protected readonly marcacoes = signal<Horario[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);

  protected readonly size = 20;
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  protected readonly iniciais = computed(() => {
    const nome = this.paciente()?.nome?.trim() ?? '';
    if (!nome) return '?';
    const partes = nome.split(/\s+/);
    const primeira = partes[0]?.[0] ?? '';
    const ultima = partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : '';
    return (primeira + ultima).toUpperCase();
  });

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
    this.termo$
      .pipe(
        distinctUntilChanged(),
        debounceTime(250),
        tap(() => this.buscando.set(true)),
        switchMap((termo) =>
          (termo && termo.trim().length >= 2 ? this.pacienteService.buscarParaSelecao(termo.trim()) : of([])).pipe(
            catchError(() => of<PacienteSelecao[]>([])),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((lista) => {
        this.sugestoes.set(lista);
        this.buscando.set(false);
      });
  }

  protected selecionar(p: PacienteSelecao | undefined): void {
    this.paciente.set(p ?? null);
    this.page.set(0);
    this.marcacoes.set([]);
    this.totalElements.set(0);
    this.totalPages.set(0);
    if (p) this.carregar();
  }

  protected classeStatus(status: StatusAgendamento): string {
    return CLASSE_STATUS[status] ?? 'st--cancelado';
  }

  protected tentarNovamente(): void {
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
    const paciente = this.paciente();
    if (!paciente) return;
    this.loading.set(true);
    this.error.set(false);
    this.service.marcacoesPorPaciente(paciente.id, this.auth.unidadeId(), this.page(), this.size).subscribe({
      next: (pagina) => {
        this.marcacoes.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
      },
      error: () => {
        this.marcacoes.set([]);
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
