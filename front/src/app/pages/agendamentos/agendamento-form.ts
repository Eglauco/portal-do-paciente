import { afterNextRender, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { Especialidade } from '../especialidades/especialidade.model';
import { EspecialidadeService } from '../especialidades/especialidade.service';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Procedimento } from '../procedimentos/procedimento.model';
import { ProcedimentoService } from '../procedimentos/procedimento.service';
import { ProfissionalSaude } from '../profissionais/profissional.model';
import { ProfissionalSaudeService } from '../profissionais/profissional.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import { AgendamentoRequest, Ref, STATUS_OPTIONS, StatusAgendamento } from './agendamento.model';
import { AgendamentoService } from './agendamento.service';

type Campo =
  | 'dataHora'
  | 'especialidadeId'
  | 'profissionalSaudeId'
  | 'procedimentoId'
  | 'pacienteId'
  | 'unidadeSaudeId';

@Component({
  selector: 'app-agendamento-form',
  imports: [ReactiveFormsModule, NgSelectModule],
  templateUrl: './agendamento-form.html',
})
export class AgendamentoForm implements PodeSair {
  private readonly service = inject(AgendamentoService);
  private readonly especialidadeService = inject(EspecialidadeService);
  private readonly profissionalService = inject(ProfissionalSaudeService);
  private readonly procedimentoService = inject(ProcedimentoService);
  private readonly pacienteService = inject(PacienteService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly especialidades = signal<Especialidade[]>([]);
  protected readonly profissionais = signal<ProfissionalSaude[]>([]);
  protected readonly procedimentos = signal<Procedimento[]>([]);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly unidades = signal<Unidade[]>([]);

  protected readonly form = new FormGroup({
    dataHora: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    especialidadeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    profissionalSaudeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    procedimentoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    pacienteId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    unidadeSaudeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    statusAgendamento: new FormControl<StatusAgendamento | null>(null),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  // Justificativa da falta (somente leitura, preenchida pelo paciente no app).
  protected readonly faltaJustificada = signal(false);
  protected readonly justificativaFalta = signal<string | null>(null);
  protected readonly motivosFalta = signal<Ref[]>([]);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    afterNextRender(() => {
      this.carregarOpcoes();
      if (this.editando()) this.carregarAgendamento();
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: Campo): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const v = this.form.getRawValue();
    const dados: AgendamentoRequest = {
      dataHora: v.dataHora,
      especialidadeId: v.especialidadeId!,
      profissionalSaudeId: v.profissionalSaudeId!,
      procedimentoId: v.procedimentoId!,
      pacienteId: v.pacienteId!,
      unidadeSaudeId: v.unidadeSaudeId!,
    };
    // Status só é enviado na edição; na criação o backend define AGUARDANDO_CONFIRMACAO_PACIENTE.
    if (this.editando() && v.statusAgendamento) {
      dados.statusAgendamento = v.statusAgendamento;
    }
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Agendamento salvo');
        this.router.navigate(['/agendamentos']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar o agendamento.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o agendamento?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Agendamento excluído');
        this.router.navigate(['/agendamentos']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o agendamento.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/agendamentos']);
  }

  private carregarOpcoes(): void {
    this.especialidadeService.listar({}, 0, 100).subscribe({
      next: (p) => this.especialidades.set(p.content),
    });
    this.profissionalService.listar({}, 0, 100).subscribe({
      next: (p) => this.profissionais.set(p.content),
    });
    this.procedimentoService.listar({}, 0, 100).subscribe({
      next: (p) => this.procedimentos.set(p.content),
    });
    this.pacienteService.listar({}, 0, 100).subscribe({
      next: (p) => this.pacientes.set(p.content),
    });
    this.unidadeService.listar({}, 0, 100).subscribe({
      next: (p) => this.unidades.set(p.content),
    });
  }

  private carregarAgendamento(): void {
    this.service.buscarPorId(this.codigo()!).subscribe({
      next: (a) => {
        this.form.patchValue({
          dataHora: a.dataHora?.slice(0, 16),
          especialidadeId: a.especialidade.id,
          profissionalSaudeId: a.profissionalSaude.id,
          procedimentoId: a.procedimento.id,
          pacienteId: a.paciente.id,
          unidadeSaudeId: a.unidadeSaude.id,
          statusAgendamento: a.statusAgendamento,
        });
        this.faltaJustificada.set(a.faltaJustificada ?? false);
        this.justificativaFalta.set(a.justificativaFalta ?? null);
        this.motivosFalta.set(a.motivosFalta ?? []);
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  private confirmar(mensagem: string): Promise<boolean> {
    this.confirmacao.set(mensagem);
    return new Promise<boolean>((resolve) => {
      this.resolverConfirmacao = resolve;
    });
  }

  protected responderConfirmacao(resposta: boolean): void {
    this.confirmacao.set(null);
    this.resolverConfirmacao?.(resposta);
    this.resolverConfirmacao = null;
  }
}
