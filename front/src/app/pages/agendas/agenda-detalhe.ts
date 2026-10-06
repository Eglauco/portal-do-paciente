import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import {
  AgendamentoEntrega,
  AgendamentoLog,
  entregaLabel,
  STATUS_OPTIONS,
} from '../agendamentos/agendamento.model';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Agenda, Horario, HorarioRequest, StatusAgendamento } from './agenda.model';
import { AgendaService } from './agenda.service';

/** Um destinatário e a linha do tempo dos seus eventos de entrega (append-only). */
interface DestinatarioEntrega {
  chave: string;
  nome: string;
  tipo: 'PACIENTE' | 'RESPONSAVEL';
  telefone: string | null;
  eventos: AgendamentoEntrega[];
}

/** Detalhe da Agenda: o slot + a lista de Horários (pacientes), com adicionar/remover e troca de status. */
@Component({
  selector: 'app-agenda-detalhe',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink],
  templateUrl: './agenda-detalhe.html',
  styles: [`
    .meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.5rem; color: var(--muted); font-size: 0.9rem; margin-bottom: 1rem; }
    .meta b { color: var(--ink); font-weight: 600; }
    .add-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; margin-bottom: 1rem; padding: 0.9rem; border: 1px dashed var(--line); border-radius: 0.6rem; }
    .add-row .form-field { margin: 0; }
    /* Paciente ocupa o espaço que sobra; os campos de hora e o botão ficam fixos à direita. */
    .add-row .form-field--paciente { flex: 1 1 320px; min-width: 240px; }
    .add-row .form-field--hora { flex: 0 0 auto; width: 130px; }
    .add-row .btn { flex: 0 0 auto; }

    /* Modal de detalhes do horário (histórico + destinatários). */
    .modal__overlay { position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,0.45); display: flex; align-items: flex-start; justify-content: center; padding: 3rem 1rem; overflow: auto; }
    .modal { width: 100%; max-width: 640px; background: var(--surface, #fff); color: var(--ink); border-radius: 0.8rem; box-shadow: 0 16px 48px rgba(0,0,0,0.22); }
    .modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.25rem 0.5rem; }
    .modal__title { font-size: 1.05rem; font-weight: 700; margin: 0; }
    .modal__sub { font-size: 0.85rem; color: var(--muted); margin: 0.15rem 0 0; }
    .modal__x { border: 0; background: transparent; font-size: 1.4rem; line-height: 1; color: var(--muted); cursor: pointer; padding: 0 0.25rem; }
    .modal__body { padding: 0.5rem 1.25rem 1.25rem; }
    .painel-tit { font-size: 0.74rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; color: var(--muted); margin: 1rem 0 0.5rem; }
    /* Timeline de status */
    .tl { list-style: none; margin: 0; padding: 0; }
    .tl__item { display: flex; gap: 0.6rem; padding: 0.45rem 0; border-bottom: 1px solid var(--line); }
    .tl__item:last-child { border-bottom: 0; }
    .tl__dot { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: var(--brand); margin-top: 0.4rem; flex: none; }
    .tl__dot--resp { background: #e6a700; }
    .tl__txt { font-size: 0.88rem; }
    .tl__meta { font-size: 0.78rem; color: var(--muted); }
    /* Destinatários */
    .dest { padding: 0.5rem 0; border-bottom: 1px solid var(--line); }
    .dest:last-child { border-bottom: 0; }
    .dest__nome { font-size: 0.9rem; font-weight: 600; }
    .dest__sub { font-size: 0.8rem; color: var(--muted); }
    .pill { display: inline-block; margin-top: 0.25rem; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--brand-deep, var(--brand)); }
  `],
})
export class AgendaDetalhe {
  private readonly service = inject(AgendaService);
  private readonly pacienteService = inject(PacienteService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);

  protected readonly statusOpcoes = STATUS_OPTIONS;

  private idAtual: number | null = null;
  protected readonly agenda = signal<Agenda | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly adicionando = signal(false);
  protected readonly excluindoAgenda = signal(false);
  /** Ids de horários em operação (trocando status / removendo), para desabilitar os controles. */
  protected readonly ocupados = signal<Set<number>>(new Set());

  protected readonly novoHorario = new FormGroup({
    pacienteId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    horaInicio: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    horaFim: new FormControl<string>('', { nonNullable: true }),
  });

  // --- Detalhes de um horário (modal): histórico de status + destinatários da notificação ---
  protected readonly detalheHorario = signal<Horario | null>(null);
  protected readonly logs = signal<AgendamentoLog[]>([]);
  protected readonly entregas = signal<AgendamentoEntrega[]>([]);
  protected readonly carregandoDetalhe = signal(false);
  protected readonly rotuloEntrega = entregaLabel;

  /** Eventos de entrega agrupados por destinatário (append-only → linha do tempo por pessoa). */
  protected readonly entregasPorPessoa = computed<DestinatarioEntrega[]>(() => {
    const grupos: DestinatarioEntrega[] = [];
    const porChave = new Map<string, DestinatarioEntrega>();
    for (const e of this.entregas()) {
      const chave =
        e.tipo === 'PACIENTE'
          ? 'PACIENTE'
          : e.responsavelId != null
            ? `RESP-${e.responsavelId}`
            : `RESP-${e.telefone}|${e.nome}`;
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

  protected abrirDetalhe(h: Horario): void {
    this.detalheHorario.set(h);
    this.logs.set([]);
    this.entregas.set([]);
    this.carregandoDetalhe.set(true);
    this.service.logsHorario(h.id).subscribe({
      next: (l) => this.logs.set(l),
      error: () => this.logs.set([]),
    });
    this.service.entregaHorario(h.id).subscribe({
      next: (e) => {
        this.entregas.set(e);
        this.carregandoDetalhe.set(false);
      },
      error: () => this.carregandoDetalhe.set(false),
    });
  }

  protected fecharDetalhe(): void {
    this.detalheHorario.set(null);
  }

  protected autorResponsavel(log: AgendamentoLog): boolean {
    return log.autor === 'RESPONSAVEL';
  }

  protected descreverAutor(log: AgendamentoLog): string {
    if (log.autor === 'RESPONSAVEL') {
      return log.responsavelNome ? `${log.responsavelNome} (responsável)` : 'Responsável';
    }
    if (log.autor === 'PACIENTE') {
      return log.pacienteNome ?? 'Paciente';
    }
    return log.usuarioNome ? `Unidade · ${log.usuarioNome}` : 'Unidade';
  }

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    this.idAtual = id ? Number(id) : null;
    afterNextRender(() => {
      if (this.idAtual != null) this.carregar();
      this.pacienteService.listar({}, 0, 100).subscribe({ next: (p) => this.pacientes.set(p.content) });
    });
  }

  protected ocupado(id: number): boolean {
    return this.ocupados().has(id);
  }

  /** Adiciona um paciente (horário) à agenda. */
  protected adicionar(): void {
    const a = this.agenda();
    if (!a || this.adicionando()) return;
    if (this.novoHorario.invalid) {
      this.novoHorario.markAllAsTouched();
      return;
    }
    const v = this.novoHorario.getRawValue();
    const req: HorarioRequest = {
      agendaId: a.id,
      pacienteId: v.pacienteId!,
      horaInicio: v.horaInicio,
      horaFim: v.horaFim || null,
    };
    this.adicionando.set(true);
    this.service.criarHorario(req).subscribe({
      next: () => {
        this.adicionando.set(false);
        this.novoHorario.reset({ pacienteId: null, horaInicio: '', horaFim: '' });
        this.toastr.success('Horário adicionado.');
        this.carregar();
      },
      error: () => {
        this.adicionando.set(false);
        this.toastr.error('Não foi possível adicionar o horário.');
      },
    });
  }

  /** Troca o status de um horário (dispara NPS/TCLE/push no backend). */
  protected trocarStatus(h: Horario, status: StatusAgendamento): void {
    if (status === h.statusAgendamento || this.ocupado(h.id)) return;
    const req: HorarioRequest = {
      agendaId: h.agendaId,
      pacienteId: h.paciente.id,
      horaInicio: h.horaInicio,
      horaFim: h.horaFim ?? null,
      statusAgendamento: status,
    };
    this.marcarOcupado(h.id, true);
    this.service.atualizarHorario(h.id, req).subscribe({
      next: () => {
        this.marcarOcupado(h.id, false);
        this.toastr.success('Status atualizado.');
        this.carregar();
      },
      error: () => {
        this.marcarOcupado(h.id, false);
        this.toastr.error('Não foi possível atualizar o status.');
      },
    });
  }

  protected removerHorario(h: Horario): void {
    if (this.ocupado(h.id) || !confirm(`Remover o horário de ${h.paciente.nome}?`)) return;
    this.marcarOcupado(h.id, true);
    this.service.excluirHorario(h.id).subscribe({
      next: () => {
        this.marcarOcupado(h.id, false);
        this.toastr.success('Horário removido.');
        this.carregar();
      },
      error: () => {
        this.marcarOcupado(h.id, false);
        this.toastr.error('Não foi possível remover o horário.');
      },
    });
  }

  protected excluirAgenda(): void {
    const a = this.agenda();
    if (!a || this.excluindoAgenda()) return;
    if ((a.horarios?.length ?? 0) > 0) {
      this.toastr.error('Remova os horários antes de excluir a agenda.');
      return;
    }
    if (!confirm('Excluir esta agenda?')) return;
    this.excluindoAgenda.set(true);
    this.service.excluir(a.id).subscribe({
      next: () => {
        this.toastr.success('Agenda excluída.');
        this.router.navigate(['/agendas']);
      },
      error: () => {
        this.excluindoAgenda.set(false);
        this.toastr.error('Não foi possível excluir a agenda.');
      },
    });
  }

  private marcarOcupado(id: number, on: boolean): void {
    const s = new Set(this.ocupados());
    if (on) s.add(id);
    else s.delete(id);
    this.ocupados.set(s);
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.buscarPorId(this.idAtual!).subscribe({
      next: (a) => {
        this.agenda.set(a);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }
}
