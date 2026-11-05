import { DatePipe } from '@angular/common';
import { Component, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { NomePaciente } from '../../shared/nome-paciente';
import { Paciente, refExibicao } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Agenda, HorarioRequest } from './agenda.model';
import { AgendaService } from './agenda.service';

/**
 * Detalhe da Agenda: o slot + a lista de Horários (pacientes). Adiciona paciente e abre o detalhe de cada horário.
 * A troca de status e a remoção do horário ficam na tela do horário ({@code /horarios/:id}) — aqui é só leitura.
 */
@Component({
  selector: 'app-agenda-detalhe',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, RouterLink, NomePaciente],
  templateUrl: './agenda-detalhe.html',
  host: { '(keydown.escape)': 'cancelarExclusao()' },
  styles: [`
    .meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.5rem; color: var(--muted); font-size: 0.9rem; margin-bottom: 1rem; }
    .meta b { color: var(--ink); font-weight: 600; }
    .add-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; margin-bottom: 1rem; padding: 0.9rem; border: 1px dashed var(--line); border-radius: 0.6rem; }
    .add-row .form-field { margin: 0; }
    .add-row .form-field--paciente { flex: 1 1 320px; min-width: 240px; }
    .add-row .form-field--hora { flex: 0 0 auto; width: 130px; }
    .add-row .btn { flex: 0 0 auto; }
    .status-pill { display: inline-block; padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--brand-deep, var(--brand)); white-space: nowrap; }
    /* Código de integração (CROSS) — badge monoespaçado para rastreio. */
    .cod { display: inline-block; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.78rem; font-weight: 600; padding: 0.05rem 0.4rem; border-radius: 0.35rem; background: color-mix(in srgb, var(--brand) 10%, transparent); color: var(--brand-deep, var(--brand)); }

    /* Modal de confirmação da exclusão da agenda. */
    .modal__overlay { position: fixed; inset: 0; z-index: 1000; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-start; justify-content: center; padding: 3rem 1rem; overflow: auto; }
    .modal { width: 100%; max-width: 540px; background: var(--surface, #fff); color: var(--ink); border-radius: 0.8rem; box-shadow: 0 16px 48px rgba(0, 0, 0, 0.22); }
    .modal:focus { outline: none; }
    .modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.25rem 0.5rem; }
    .modal__title { font-size: 1.1rem; font-weight: 700; margin: 0; }
    .modal__x { border: 0; background: transparent; font-size: 1.4rem; line-height: 1; color: var(--muted); cursor: pointer; padding: 0 0.25rem; }
    .modal__body { padding: 0.25rem 1.25rem 1rem; font-size: 0.92rem; line-height: 1.5; }
    .modal__body p { margin: 0 0 0.6rem; }
    .modal__foot { display: flex; align-items: center; justify-content: flex-end; gap: 0.5rem; padding: 0.75rem 1.25rem 1.1rem; border-top: 1px solid var(--line); }
    .excl-alert { display: flex; gap: 0.55rem; align-items: flex-start; padding: 0.65rem 0.8rem; border-radius: 0.5rem; background: color-mix(in srgb, #f04438 12%, transparent); color: #b42318; margin: 0 0 0.85rem; font-weight: 600; }
    .excl-alert svg { flex: 0 0 auto; width: 20px; height: 20px; margin-top: 0.05rem; }
    .excl-resumo { margin: 0.5rem 0 0.75rem; padding: 0.6rem 0.8rem; border: 1px solid var(--line); border-radius: 0.5rem; list-style: none; }
    .excl-resumo li { margin: 0.12rem 0; }
    .excl-resumo .muted { color: var(--muted); }
    .excl-nota { font-size: 0.82rem; color: var(--muted); margin: 0.5rem 0 0; }
    .btn--danger { background: #b42318; color: #fff; border: 1px solid #b42318; }
    .btn--danger:hover:not(:disabled) { background: #912014; border-color: #912014; }
    .btn--danger:disabled { opacity: 0.6; cursor: default; }
  `],
})
export class AgendaDetalhe {
  private readonly service = inject(AgendaService);
  private readonly pacienteService = inject(PacienteService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);

  private idAtual: number | null = null;
  /** Converte a entidade Paciente na forma do <app-nome-paciente> (nos itens do seletor). */
  protected readonly refExibicao = refExibicao;
  protected readonly agenda = signal<Agenda | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly adicionando = signal(false);
  protected readonly excluindoAgenda = signal(false);
  /** Mostra o modal de confirmação da exclusão da agenda (que remove também todos os horários). */
  protected readonly confirmandoExclusao = signal(false);
  private readonly dialogExclusao = viewChild<ElementRef<HTMLElement>>('dialogExclusao');
  /** Botão que abre o modal — para devolver o foco a ele quando o modal fecha (acessibilidade). */
  private readonly btnExcluir = viewChild<ElementRef<HTMLButtonElement>>('btnExcluir');

  protected readonly novoHorario = new FormGroup({
    pacienteId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    horaInicio: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
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
    };
    this.adicionando.set(true);
    this.service.criarHorario(req).subscribe({
      next: () => {
        this.adicionando.set(false);
        this.novoHorario.reset({ pacienteId: null, horaInicio: '' });
        this.toastr.success('Horário adicionado.');
        this.carregar();
      },
      error: () => {
        this.adicionando.set(false);
        this.toastr.error('Não foi possível adicionar o horário.');
      },
    });
  }

  /** Abre o modal de confirmação (bem explicativo: avisa que TODOS os horários da agenda serão removidos). */
  protected abrirExclusao(): void {
    if (this.excluindoAgenda()) return;
    this.confirmandoExclusao.set(true);
    // Acessibilidade: move o foco para o diálogo (leitor de tela o anuncia) — o fundo fica `inert` enquanto aberto
    // (o Tab não escapa) e o Esc fecha.
    setTimeout(() => this.dialogExclusao()?.nativeElement.focus(), 0);
  }

  protected cancelarExclusao(): void {
    if (this.excluindoAgenda()) return;
    this.confirmandoExclusao.set(false);
    // Devolve o foco ao botão que abriu o modal (padrão WAI-ARIA de diálogo) — depois que o `inert` do fundo sai.
    setTimeout(() => this.btnExcluir()?.nativeElement.focus(), 0);
  }

  /** Confirma: o backend remove TODOS os horários da agenda e exclui a agenda numa única transação. */
  protected confirmarExclusao(): void {
    const a = this.agenda();
    if (!a || this.excluindoAgenda()) return;
    const qtd = a.horarios?.length ?? 0;
    this.excluindoAgenda.set(true);
    this.service.excluir(a.id).subscribe({
      next: () => {
        this.toastr.success(qtd > 0 ? `Agenda e ${qtd} horário(s) excluídos.` : 'Agenda excluída.');
        this.router.navigate(['/agendas']);
      },
      error: (e) => {
        this.excluindoAgenda.set(false);
        // Mostra o motivo vindo do backend (ex.: horário com prontuário/NPS) em vez de um erro genérico.
        this.toastr.error(e?.error?.message ?? 'Não foi possível excluir a agenda.');
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
