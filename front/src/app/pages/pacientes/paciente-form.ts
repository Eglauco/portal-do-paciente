import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { switchMap } from 'rxjs';
import { PodeSair } from '../../core/pending-changes.guard';
import { TelefoneBrDirective } from '../../shared/telefone-br.directive';
import { CodigoAtivacao } from './paciente.model';
import { PacienteService } from './paciente.service';

@Component({
  selector: 'app-paciente-form',
  imports: [ReactiveFormsModule, TelefoneBrDirective],
  templateUrl: './paciente-form.html',
})
export class PacienteForm implements PodeSair {
  private readonly service = inject(PacienteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    telefone: new FormControl('', { nonNullable: true }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  // Acesso ao app
  protected readonly ativo = signal(false);
  protected readonly gerandoCodigo = signal(false);
  protected readonly revogando = signal(false);
  protected readonly codigoAtivacao = signal<CodigoAtivacao | null>(null);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.editando.set(true);
      this.codigo.set(id);
      this.service.buscarPorId(id).subscribe({
        next: (paciente) => {
          this.form.patchValue({ nome: paciente.nome, telefone: paciente.telefone ?? '' });
          this.ativo.set(!!paciente.ativo);
        },
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  private valores() {
    return {
      nome: this.form.controls.nome.value.trim(),
      telefone: this.form.controls.telefone.value.trim() || null,
    };
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, this.valores())
      : this.service.criar(this.valores());
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Paciente salvo');
        this.router.navigate(['/pacientes']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(
          e?.status === 409 ? 'Já existe um paciente com este telefone.' : 'Não foi possível salvar o paciente.',
        );
      },
    });
  }

  /** Garante que o telefone está salvo e gera um novo código de ativação. */
  protected gerarCodigo(): void {
    if (this.gerandoCodigo()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.form.controls.telefone.value.trim()) {
      this.toastr.error('Informe o telefone do paciente para gerar o código.');
      return;
    }
    const id = this.codigo()!;
    this.gerandoCodigo.set(true);
    this.service
      .atualizar(id, this.valores())
      .pipe(switchMap(() => this.service.gerarCodigo(id)))
      .subscribe({
        next: (codigo) => {
          this.form.markAsPristine();
          this.ativo.set(true);
          this.codigoAtivacao.set(codigo);
          this.gerandoCodigo.set(false);
        },
        error: (e) => {
          this.gerandoCodigo.set(false);
          this.toastr.error(
            e?.status === 409 ? 'Já existe um paciente com este telefone.' : 'Não foi possível gerar o código.',
          );
        },
      });
  }

  protected copiarCodigo(): void {
    const codigo = this.codigoAtivacao()?.codigo;
    if (codigo) {
      navigator.clipboard?.writeText(codigo).then(
        () => this.toastr.success('Código copiado'),
        () => {},
      );
    }
  }

  protected fecharCodigo(): void {
    this.codigoAtivacao.set(null);
  }

  protected async revogar(): Promise<void> {
    const confirmado = await this.confirmar(
      'Revogar o acesso do paciente ao app? Ele precisará de um novo código para entrar.',
    );
    if (!confirmado) return;
    this.revogando.set(true);
    this.service.revogarAcesso(this.codigo()!).subscribe({
      next: () => {
        this.ativo.set(false);
        this.revogando.set(false);
        this.toastr.success('Acesso revogado');
      },
      error: () => {
        this.revogando.set(false);
        this.toastr.error('Não foi possível revogar o acesso.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o paciente?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Paciente excluído');
        this.router.navigate(['/pacientes']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o paciente.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/pacientes']);
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
