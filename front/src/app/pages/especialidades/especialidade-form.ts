import { afterNextRender, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { ConfiguracaoAgenda } from '../configuracao-agenda/configuracao-agenda.model';
import { ConfiguracaoAgendaService } from '../configuracao-agenda/configuracao-agenda.service';
import { EspecialidadeService } from './especialidade.service';

@Component({
  selector: 'app-especialidade-form',
  imports: [ReactiveFormsModule, NgSelectModule],
  templateUrl: './especialidade-form.html',
})
export class EspecialidadeForm implements PodeSair {
  private readonly service = inject(EspecialidadeService);
  private readonly configuracaoAgendaService = inject(ConfiguracaoAgendaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly configuracaoAgendas = signal<ConfiguracaoAgenda[]>([]);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    codigoIntegracao: new FormControl('', { nonNullable: true }),
    configuracaoAgendaId: new FormControl<number | null>(null),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    // SSR-safe: as chamadas HTTP só rodam no navegador (não no prerender de /novo nem no SSR),
    // com tratamento de erro — evita falha em tempo de build e padroniza com paciente/profissional.
    afterNextRender(() => {
      this.configuracaoAgendaService.listar({}, 0, 100).subscribe({
        next: (p) => this.configuracaoAgendas.set(p.content),
        error: () => {},
      });
      const id = this.codigo();
      if (id != null) {
        this.service.buscarPorId(id).subscribe({
          next: (especialidade) =>
            this.form.patchValue({
              nome: especialidade.nome,
              codigoIntegracao: especialidade.codigoIntegracao ?? '',
              configuracaoAgendaId: especialidade.configuracaoAgenda?.id ?? null,
            }),
          error: () => this.erroCarregar.set(true),
        });
      }
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome'): boolean {
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
    const codigo = this.form.controls.codigoIntegracao.value.trim();
    const procId = this.form.controls.configuracaoAgendaId.value;
    const dados = {
      nome: this.form.controls.nome.value,
      codigoIntegracao: codigo || null,
      configuracaoAgenda: procId ? { id: procId } : null,
    };
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Especialidade salva');
        this.router.navigate(['/especialidades']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(
          e?.status === 409
            ? 'Já existe uma especialidade com este código de integração.'
            : 'Não foi possível salvar a especialidade.',
        );
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir a especialidade?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Especialidade excluída');
        this.router.navigate(['/especialidades']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir a especialidade.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/especialidades']);
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
