import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import { PodeSair } from '../../core/pending-changes.guard';
import { Ref } from '../agendamentos/agendamento.model';
import { ConfiguracaoAgenda } from '../configuracao-agenda/configuracao-agenda.model';
import { ConfiguracaoAgendaService } from '../configuracao-agenda/configuracao-agenda.service';
import { ProfissionalSaude } from '../profissionais/profissional.model';
import { ProfissionalSaudeService } from '../profissionais/profissional.service';
import { AgendaRequest } from './agenda.model';
import { AgendaService } from './agenda.service';

type Campo = 'data' | 'especialidadeId' | 'profissionalSaudeId' | 'configuracaoAgendaId' | 'unidadeSaudeId';

/** Cadastro/edição de uma Agenda (slot): dia + profissional/especialidade/configuracao-agenda/unidade. */
@Component({
  selector: 'app-agenda-form',
  imports: [ReactiveFormsModule, NgSelectModule, RouterLink],
  templateUrl: './agenda-form.html',
  styles: [`
    .form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1rem 1.25rem; }
    .form-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.25rem; }
    .field-error { display: block; margin-top: 0.3rem; font-size: 0.8rem; color: #c0392b; }
  `],
})
export class AgendaForm implements PodeSair {
  private readonly service = inject(AgendaService);
  private readonly profissionalService = inject(ProfissionalSaudeService);
  private readonly configuracaoAgendaService = inject(ConfiguracaoAgendaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);
  private readonly auth = inject(AuthService);

  protected readonly unidadeNome = this.auth.unidadeNome;

  protected readonly profissionais = signal<ProfissionalSaude[]>([]);
  protected readonly configuracaoAgendas = signal<ConfiguracaoAgenda[]>([]);

  private readonly profissionalSelecionadoId = signal<number | null>(null);
  private readonly profissionalSalvado = signal<Ref | null>(null);
  private readonly especialidadeSalvada = signal<Ref | null>(null);

  /** Só os profissionais da unidade ativa (+ o já gravado na edição). */
  protected readonly profissionaisDisponiveis = computed<Ref[]>(() => {
    const unidadeId = this.auth.unidadeId();
    const daUnidade: Ref[] = this.profissionais()
      .filter((p) => (p.unidades ?? []).some((u) => u.id === unidadeId))
      .map((p) => ({ id: p.id!, nome: p.nome }));
    const salvo = this.profissionalSalvado();
    if (salvo && !daUnidade.some((p) => p.id === salvo.id)) return [...daUnidade, salvo];
    return daUnidade;
  });

  /** Especialidades do profissional selecionado (+ a já gravada na edição). */
  protected readonly especialidadesDisponiveis = computed<Ref[]>(() => {
    const pid = this.profissionalSelecionadoId();
    const prof = this.profissionais().find((p) => p.id === pid);
    const doProf: Ref[] = (prof?.especialidades ?? []).map((e) => ({ id: e.id, nome: e.nome }));
    const salva = this.especialidadeSalvada();
    if (salva && !doProf.some((e) => e.id === salva.id)) return [...doProf, salva];
    return doProf;
  });

  protected readonly form = new FormGroup({
    data: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    profissionalSaudeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    especialidadeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    configuracaoAgendaId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    unidadeSaudeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    nome: new FormControl<string>('', { nonNullable: true }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  /** Código de integração da agenda, quando veio de um sistema externo — exibido só-leitura para rastreio. */
  protected readonly codigoIntegracao = signal<string | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroCarregar = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;
  private carregando = false;

  constructor() {
    this.form.controls.especialidadeId.disable();
    this.form.controls.profissionalSaudeId.valueChanges.pipe(takeUntilDestroyed()).subscribe((pid) => {
      this.profissionalSelecionadoId.set(pid ?? null);
      const esp = this.form.controls.especialidadeId;
      if (pid != null) esp.enable({ emitEvent: false });
      else esp.disable({ emitEvent: false });
      if (this.carregando) return;
      this.profissionalSalvado.set(null);
      this.especialidadeSalvada.set(null);
      esp.reset(null);
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    afterNextRender(() => {
      this.carregarOpcoes();
      this.form.controls.unidadeSaudeId.setValue(this.auth.unidadeId());
      this.form.controls.unidadeSaudeId.disable();
      if (this.editando()) this.carregarAgenda();
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: Campo): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const v = this.form.getRawValue();
    const dados: AgendaRequest = {
      data: v.data,
      profissionalSaudeId: v.profissionalSaudeId!,
      especialidadeId: v.especialidadeId!,
      configuracaoAgendaId: v.configuracaoAgendaId!,
      unidadeSaudeId: v.unidadeSaudeId!,
      nome: v.nome?.trim() || null,
    };
    const req = this.editando() ? this.service.atualizar(this.codigo()!, dados) : this.service.criar(dados);
    req.subscribe({
      next: (a) => {
        this.saidaAutorizada = true;
        this.toastr.success('Agenda salva');
        // Depois de salvar, abre o detalhe para adicionar os horários (pacientes).
        this.router.navigate(['/agendas', a.id]);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar a agenda.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/agendas']);
  }

  private carregarOpcoes(): void {
    this.profissionalService.listar({}, 0, 100).subscribe({ next: (p) => this.profissionais.set(p.content) });
    this.configuracaoAgendaService.listar({}, 0, 100).subscribe({ next: (p) => this.configuracaoAgendas.set(p.content) });
  }

  private carregarAgenda(): void {
    this.service.buscarPorId(this.codigo()!).subscribe({
      next: (a) => {
        this.carregando = true;
        this.profissionalSalvado.set(a.profissionalSaude);
        this.especialidadeSalvada.set(a.especialidade);
        this.codigoIntegracao.set(a.codigoIntegracao ?? null);
        this.form.patchValue({
          data: a.data,
          profissionalSaudeId: a.profissionalSaude.id,
          configuracaoAgendaId: a.configuracaoAgenda.id,
          nome: a.nome ?? '',
        });
        this.form.controls.especialidadeId.setValue(a.especialidade.id);
        this.carregando = false;
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  private confirmar(mensagem: string): Promise<boolean> {
    this.confirmacao.set(mensagem);
    return new Promise<boolean>((resolve) => (this.resolverConfirmacao = resolve));
  }

  protected responderConfirmacao(resposta: boolean): void {
    this.confirmacao.set(null);
    this.resolverConfirmacao?.(resposta);
    this.resolverConfirmacao = null;
  }
}
