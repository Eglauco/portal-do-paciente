import { DatePipe } from '@angular/common';
import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import { PodeSair } from '../../core/pending-changes.guard';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { Procedimento } from '../procedimentos/procedimento.model';
import { ProcedimentoService } from '../procedimentos/procedimento.service';
import { ProfissionalSaude } from '../profissionais/profissional.model';
import { ProfissionalSaudeService } from '../profissionais/profissional.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import {
  AgendamentoEntrega,
  AgendamentoLog,
  AgendamentoRequest,
  entregaLabel,
  Ref,
  STATUS_OPTIONS,
  StatusAgendamento,
} from './agendamento.model';
import { AgendamentoService } from './agendamento.service';

type Campo =
  | 'dataHora'
  | 'especialidadeId'
  | 'profissionalSaudeId'
  | 'procedimentoId'
  | 'pacienteId'
  | 'unidadeSaudeId';

/** Um destinatário e a linha do tempo dos seus eventos de entrega (append-only). */
interface DestinatarioEntrega {
  chave: string;
  nome: string;
  tipo: 'PACIENTE' | 'RESPONSAVEL';
  telefone: string | null;
  eventos: AgendamentoEntrega[];
}

@Component({
  selector: 'app-agendamento-form',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe],
  templateUrl: './agendamento-form.html',
})
export class AgendamentoForm implements PodeSair {
  private readonly service = inject(AgendamentoService);
  private readonly profissionalService = inject(ProfissionalSaudeService);
  private readonly procedimentoService = inject(ProcedimentoService);
  private readonly pacienteService = inject(PacienteService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);
  private readonly auth = inject(AuthService);

  /** Unidade logada — o agendamento é sempre criado/editado na unidade ativa. */
  protected readonly unidadeNome = this.auth.unidadeNome;

  protected readonly statusOpcoes = STATUS_OPTIONS;
  /** Todos os profissionais ativos (a lista visível é filtrada pela unidade ativa). */
  protected readonly profissionais = signal<ProfissionalSaude[]>([]);
  protected readonly procedimentos = signal<Procedimento[]>([]);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly unidades = signal<Unidade[]>([]);

  /** Profissional selecionado (fonte reativa para derivar as especialidades). */
  private readonly profissionalSelecionadoId = signal<number | null>(null);
  /** Na edição, o profissional/especialidade já gravados — mantidos visíveis mesmo fora da regra. */
  private readonly profissionalSalvado = signal<Ref | null>(null);
  private readonly especialidadeSalvada = signal<Ref | null>(null);

  /**
   * Regra do novo agendamento: só aparecem os profissionais vinculados à unidade ativa
   * (que atendem nela). Na edição, o profissional já gravado é mantido na lista mesmo que
   * não atenda mais a unidade, para não sumir com o valor do registro.
   */
  protected readonly profissionaisDisponiveis = computed<Ref[]>(() => {
    const unidadeId = this.auth.unidadeId();
    const daUnidade: Ref[] = this.profissionais()
      .filter((p) => (p.unidades ?? []).some((u) => u.id === unidadeId))
      .map((p) => ({ id: p.id!, nome: p.nome }));
    const salvo = this.profissionalSalvado();
    if (salvo && !daUnidade.some((p) => p.id === salvo.id)) {
      return [...daUnidade, salvo];
    }
    return daUnidade;
  });

  /**
   * Especialidades do profissional selecionado (as que ele atende). Só habilita depois de
   * escolher o profissional. Na edição, a especialidade já gravada é mantida na lista.
   */
  protected readonly especialidadesDisponiveis = computed<Ref[]>(() => {
    const pid = this.profissionalSelecionadoId();
    const prof = this.profissionais().find((p) => p.id === pid);
    const doProf: Ref[] = (prof?.especialidades ?? []).map((e) => ({ id: e.id, nome: e.nome }));
    const salva = this.especialidadeSalvada();
    if (salva && !doProf.some((e) => e.id === salva.id)) {
      return [...doProf, salva];
    }
    return doProf;
  });

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

  // Histórico de status: quem fez cada troca (paciente, responsável ou unidade).
  protected readonly logs = signal<AgendamentoLog[]>([]);
  protected readonly carregandoLogs = signal(false);
  protected readonly erroLogs = signal(false);

  // Destinatários da notificação e o estado de entrega de cada um (paciente + responsáveis).
  // A tabela é append-only: cada mudança é um evento; aqui agrupamos por pessoa (linha do tempo).
  protected readonly entregas = signal<AgendamentoEntrega[]>([]);
  protected readonly carregandoEntrega = signal(false);
  protected readonly erroEntrega = signal(false);
  protected readonly rotuloEntrega = entregaLabel;

  /** Eventos de entrega agrupados por destinatário, cada um com sua linha do tempo (ordem de chegada). */
  protected readonly entregasPorPessoa = computed<DestinatarioEntrega[]>(() => {
    const grupos: DestinatarioEntrega[] = [];
    const porChave = new Map<string, DestinatarioEntrega>();
    for (const e of this.entregas()) {
      // Responsável excluído do cadastro tem responsavelId nulo (FK SET NULL): cai no telefone/nome
      // congelados, senão dois responsáveis removidos se fundiriam numa só linha do tempo.
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

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;
  /** True enquanto o formulário é preenchido na edição (evita zerar a especialidade gravada). */
  private carregando = false;

  constructor() {
    // Especialidade só habilita após escolher o profissional.
    this.form.controls.especialidadeId.disable();

    // Ao trocar o profissional: deriva as especialidades dele e habilita o campo;
    // troca manual (fora da carga da edição) zera a especialidade anterior.
    this.form.controls.profissionalSaudeId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((pid) => {
        this.profissionalSelecionadoId.set(pid ?? null);
        const especialidade = this.form.controls.especialidadeId;
        if (pid != null) {
          especialidade.enable({ emitEvent: false });
        } else {
          especialidade.disable({ emitEvent: false });
        }
        if (this.carregando) return;
        // Troca manual de profissional: descarta os valores "gravados" e zera a especialidade.
        // reset() (e não setValue) também limpa touched/dirty, evitando o erro "obrigatório"
        // piscar num campo que o usuário ainda não tocou.
        this.profissionalSalvado.set(null);
        this.especialidadeSalvada.set(null);
        especialidade.reset(null);
      });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    afterNextRender(() => {
      this.carregarOpcoes();
      // Unidade travada na unidade logada (não editável).
      this.form.controls.unidadeSaudeId.setValue(this.auth.unidadeId());
      this.form.controls.unidadeSaudeId.disable();
      if (this.editando()) {
        this.carregarAgendamento();
        this.carregarLogs();
        this.carregarEntrega();
      }
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
    // Especialidades não são mais carregadas globalmente: derivam do profissional escolhido.
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
        // Mantém o profissional/especialidade gravados visíveis mesmo que hoje não batam com a regra.
        this.carregando = true;
        this.profissionalSalvado.set(a.profissionalSaude);
        this.especialidadeSalvada.set(a.especialidade);
        this.form.patchValue({
          dataHora: a.dataHora?.slice(0, 16),
          profissionalSaudeId: a.profissionalSaude.id,
          procedimentoId: a.procedimento.id,
          pacienteId: a.paciente.id,
          statusAgendamento: a.statusAgendamento,
        });
        // Especialidade depois do profissional (o valueChanges do profissional a zeraria).
        this.form.controls.especialidadeId.setValue(a.especialidade.id);
        this.carregando = false;
        this.faltaJustificada.set(a.faltaJustificada ?? false);
        this.justificativaFalta.set(a.justificativaFalta ?? null);
        this.motivosFalta.set(a.motivosFalta ?? []);
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  /** Carrega a linha do tempo de trocas de status deste agendamento (edição). */
  private carregarLogs(): void {
    if (this.codigo() == null) return;
    this.carregandoLogs.set(true);
    this.erroLogs.set(false);
    this.service.logs(this.codigo()!).subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.carregandoLogs.set(false);
      },
      error: () => {
        this.erroLogs.set(true);
        this.carregandoLogs.set(false);
      },
    });
  }

  /** Carrega os destinatários e o estado de entrega da notificação deste agendamento (edição). */
  private carregarEntrega(): void {
    if (this.codigo() == null) return;
    this.carregandoEntrega.set(true);
    this.erroEntrega.set(false);
    this.service.entrega(this.codigo()!).subscribe({
      next: (entregas) => {
        this.entregas.set(entregas);
        this.carregandoEntrega.set(false);
      },
      error: () => {
        this.erroEntrega.set(true);
        this.carregandoEntrega.set(false);
      },
    });
  }

  protected autorResponsavel(log: AgendamentoLog): boolean {
    return log.autor === 'RESPONSAVEL';
  }

  /** Texto de quem fez a troca de status (paciente, responsável ou unidade/atendente). */
  protected descreverAutor(log: AgendamentoLog): string {
    // Decide o texto pelo AUTOR (não pela presença do nome): um responsável removido
    // do cadastro zera o responsavel_id (FK SET NULL), mas a ação continua sendo dele —
    // cai num rótulo genérico, coerente com o marcador âmbar de autorResponsavel().
    if (log.autor === 'RESPONSAVEL') {
      return log.responsavelNome ? `${log.responsavelNome} (responsável)` : 'Responsável';
    }
    if (log.autor === 'PACIENTE') {
      return log.pacienteNome ?? 'Paciente';
    }
    return log.usuarioNome ? `Unidade · ${log.usuarioNome}` : 'Unidade';
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
