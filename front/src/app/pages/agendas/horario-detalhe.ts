import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import {
  AgendamentoEntrega,
  AgendamentoLog,
  entregaLabel,
  STATUS_OPTIONS,
} from '../agendamentos/agendamento.model';
import { Horario, HorarioRequest, StatusAgendamento } from './agenda.model';
import { AgendaService } from './agenda.service';

interface DestinatarioEntrega {
  chave: string;
  nome: string;
  tipo: 'PACIENTE' | 'RESPONSAVEL';
  telefone: string | null;
  eventos: AgendamentoEntrega[];
}

type AbaId = 'dados' | 'destinatarios' | 'historico';

/**
 * Tela do Horário (marcação): o detalhe dividido em abas (Dados / Destinatários / Histórico). É AQUI que se
 * troca o status e se remove o horário — a tela da agenda só lista. Centraliza as regras do horário.
 */
@Component({
  selector: 'app-horario-detalhe',
  imports: [ReactiveFormsModule, DatePipe, RouterLink],
  templateUrl: './horario-detalhe.html',
  styles: [`
    /* Cabeçalho fixo com os dados da agenda/horário (sempre visível, acima das abas). */
    .agenda-head { border: 1px solid var(--line); border-radius: 0.7rem; padding: 0.9rem 1rem; margin-bottom: 1.1rem; background: color-mix(in srgb, var(--brand) 5%, transparent); }
    .agenda-head__main { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem 0.75rem; margin-bottom: 0.6rem; }
    .agenda-head__paciente { font-size: 1.05rem; font-weight: 700; color: var(--ink); }
    .agenda-head__grid { display: flex; flex-wrap: wrap; gap: 0.35rem 1.5rem; font-size: 0.88rem; color: var(--muted); }
    .agenda-head__grid b { color: var(--ink); font-weight: 600; }
    .form-tabs { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 1.25rem; border-bottom: 1px solid var(--line); }
    .form-tab { position: relative; display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.6rem 0.95rem; border: none; background: none; cursor: pointer; font-size: 0.9rem; font-weight: 600; color: var(--muted); border-bottom: 2px solid transparent; margin-bottom: -1px; border-radius: 0.4rem 0.4rem 0 0; }
    .form-tab:hover { color: var(--ink); background: color-mix(in srgb, var(--brand) 8%, transparent); }
    .form-tab--ativa { color: var(--brand-deep, var(--brand)); border-bottom-color: var(--brand); }
    .status-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; margin: 0.5rem 0 1.25rem; }
    .status-row .form-field { margin: 0; min-width: 280px; }
    .falta-box { padding: 0.8rem 0.9rem; border: 1px solid #e6c200; background: color-mix(in srgb, #ffcc00 10%, transparent); border-radius: 0.55rem; margin-bottom: 1rem; }
    .tl { list-style: none; margin: 0; padding: 0; }
    .tl__item { display: flex; gap: 0.6rem; padding: 0.5rem 0; border-bottom: 1px solid var(--line); }
    .tl__item:last-child { border-bottom: 0; }
    .tl__dot { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: var(--brand); margin-top: 0.4rem; flex: none; }
    .tl__dot--resp { background: #e6a700; }
    .tl__meta { font-size: 0.78rem; color: var(--muted); }
    .dest { padding: 0.55rem 0; border-bottom: 1px solid var(--line); }
    .dest:last-child { border-bottom: 0; }
    .dest__nome { font-size: 0.92rem; font-weight: 600; }
    .dest__sub { font-size: 0.8rem; color: var(--muted); }
    .pill { display: inline-block; margin-top: 0.25rem; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--brand-deep, var(--brand)); }
  `],
})
export class HorarioDetalhe {
  private readonly service = inject(AgendaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);

  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly rotuloEntrega = entregaLabel;

  private idAtual: number | null = null;
  protected readonly abaAtiva = signal<AbaId>('dados');
  protected readonly horario = signal<Horario | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly salvandoStatus = signal(false);
  protected readonly removendo = signal(false);

  protected readonly statusControl = new FormControl<StatusAgendamento | null>(null);

  protected readonly logs = signal<AgendamentoLog[]>([]);
  protected readonly entregas = signal<AgendamentoEntrega[]>([]);

  protected readonly entregasPorPessoa = computed<DestinatarioEntrega[]>(() => {
    const grupos: DestinatarioEntrega[] = [];
    const porChave = new Map<string, DestinatarioEntrega>();
    for (const e of this.entregas()) {
      const chave = e.tipo === 'PACIENTE' ? 'PACIENTE'
        : e.responsavelId != null ? `RESP-${e.responsavelId}` : `RESP-${e.telefone}|${e.nome}`;
      let grupo = porChave.get(chave);
      if (!grupo) {
        grupo = { chave, nome: e.nome, tipo: e.tipo, telefone: e.telefone, eventos: [] };
        porChave.set(chave, grupo);
        grupos.push(grupo);
      }
      grupo.eventos.push(e);
    }
    return grupos;
  });

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    this.idAtual = id ? Number(id) : null;
    afterNextRender(() => {
      if (this.idAtual != null) {
        this.carregar();
        this.service.logsHorario(this.idAtual).subscribe({ next: (l) => this.logs.set(l) });
        this.service.entregaHorario(this.idAtual).subscribe({ next: (e) => this.entregas.set(e) });
      }
    });
  }

  protected selecionarAba(id: AbaId): void {
    this.abaAtiva.set(id);
  }

  protected voltarUrl(): unknown[] {
    const h = this.horario();
    return h ? ['/agendas', h.agendaId] : ['/agendas'];
  }

  /** Salva a troca de status (dispara NPS/TCLE/push no backend). */
  protected salvarStatus(): void {
    const h = this.horario();
    const status = this.statusControl.value;
    if (!h || !status || this.salvandoStatus()) return;
    if (status === h.statusAgendamento) {
      this.toastr.info('O status já é esse.');
      return;
    }
    const req: HorarioRequest = {
      agendaId: h.agendaId,
      pacienteId: h.paciente.id,
      horaInicio: (h.horaInicio ?? '').slice(0, 5),
      horaFim: h.horaFim ? h.horaFim.slice(0, 5) : null,
      statusAgendamento: status,
    };
    this.salvandoStatus.set(true);
    this.service.atualizarHorario(h.id, req).subscribe({
      next: () => {
        this.salvandoStatus.set(false);
        this.toastr.success('Status atualizado.');
        this.carregar();
        if (this.idAtual != null) {
          this.service.logsHorario(this.idAtual).subscribe({ next: (l) => this.logs.set(l) });
        }
      },
      error: () => {
        this.salvandoStatus.set(false);
        this.toastr.error('Não foi possível atualizar o status.');
      },
    });
  }

  protected remover(): void {
    const h = this.horario();
    if (!h || this.removendo() || !confirm(`Remover o horário de ${h.paciente.nome}?`)) return;
    this.removendo.set(true);
    const agendaId = h.agendaId;
    this.service.excluirHorario(h.id).subscribe({
      next: () => {
        this.toastr.success('Horário removido.');
        this.router.navigate(['/agendas', agendaId]);
      },
      error: () => {
        this.removendo.set(false);
        this.toastr.error('Não foi possível remover o horário.');
      },
    });
  }

  protected autorResponsavel(log: AgendamentoLog): boolean {
    return log.autor === 'RESPONSAVEL';
  }

  protected descreverAutor(log: AgendamentoLog): string {
    if (log.autor === 'RESPONSAVEL') return log.responsavelNome ? `${log.responsavelNome} (responsável)` : 'Responsável';
    if (log.autor === 'PACIENTE') return log.pacienteNome ?? 'Paciente';
    return log.usuarioNome ? `Unidade · ${log.usuarioNome}` : 'Unidade';
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.buscarHorario(this.idAtual!).subscribe({
      next: (h) => {
        this.horario.set(h);
        this.statusControl.setValue(h.statusAgendamento);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }
}
