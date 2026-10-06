import { DatePipe } from '@angular/common';
import { Component, afterNextRender, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { STATUS_OPTIONS } from '../agendamentos/agendamento.model';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Agenda, Horario, HorarioRequest, StatusAgendamento } from './agenda.model';
import { AgendaService } from './agenda.service';

/** Detalhe da Agenda: o slot + a lista de Horários (pacientes), com adicionar/remover e troca de status. */
@Component({
  selector: 'app-agenda-detalhe',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink],
  templateUrl: './agenda-detalhe.html',
  styles: [`
    .meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.5rem; color: var(--muted); font-size: 0.9rem; margin-bottom: 1rem; }
    .meta b { color: var(--ink); font-weight: 600; }
    .add-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; margin-bottom: 1rem; padding: 0.9rem; border: 1px dashed var(--line); border-radius: 0.6rem; }
    .add-row .form-field { margin: 0; min-width: 220px; }
    .add-row .form-field--hora { min-width: 130px; }
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
