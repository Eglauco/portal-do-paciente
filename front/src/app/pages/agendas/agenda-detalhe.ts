import { DatePipe } from '@angular/common';
import { Component, afterNextRender, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Agenda, HorarioRequest } from './agenda.model';
import { AgendaService } from './agenda.service';

/**
 * Detalhe da Agenda: o slot + a lista de Horários (pacientes). Adiciona paciente e abre o detalhe de cada horário.
 * A troca de status e a remoção do horário ficam na tela do horário ({@code /horarios/:id}) — aqui é só leitura.
 */
@Component({
  selector: 'app-agenda-detalhe',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink],
  templateUrl: './agenda-detalhe.html',
  styles: [`
    .meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.5rem; color: var(--muted); font-size: 0.9rem; margin-bottom: 1rem; }
    .meta b { color: var(--ink); font-weight: 600; }
    .add-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; margin-bottom: 1rem; padding: 0.9rem; border: 1px dashed var(--line); border-radius: 0.6rem; }
    .add-row .form-field { margin: 0; }
    .add-row .form-field--paciente { flex: 1 1 320px; min-width: 240px; }
    .add-row .form-field--hora { flex: 0 0 auto; width: 130px; }
    .add-row .btn { flex: 0 0 auto; }
    .status-pill { display: inline-block; padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--brand-deep, var(--brand)); white-space: nowrap; }
  `],
})
export class AgendaDetalhe {
  private readonly service = inject(AgendaService);
  private readonly pacienteService = inject(PacienteService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);

  private idAtual: number | null = null;
  protected readonly agenda = signal<Agenda | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly adicionando = signal(false);
  protected readonly excluindoAgenda = signal(false);

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
