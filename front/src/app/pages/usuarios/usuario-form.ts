import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { UsuarioService } from './usuario.service';

@Component({
  selector: 'app-usuario-form',
  imports: [ReactiveFormsModule],
  templateUrl: './usuario-form.html',
})
export class UsuarioForm implements PodeSair {
  private readonly service = inject(UsuarioService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  // Diálogo de confirmação
  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;

  // Marca uma saída já autorizada (após salvar/excluir ou cancelar confirmado).
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.editando.set(true);
      this.codigo.set(id);
      this.service.buscarPorId(id).subscribe({
        next: (usuario) => this.form.patchValue({ nome: usuario.nome, email: usuario.email }),
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  /** Chamado pelo guard de rota antes de sair. */
  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome' | 'email'): boolean {
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
    const dados = {
      nome: this.form.controls.nome.value,
      email: this.form.controls.email.value,
    };
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Usuário salvo');
        this.router.navigate(['/usuarios']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar o usuário.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o usuário?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Usuário excluído');
        this.router.navigate(['/usuarios']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o usuário.');
      },
    });
  }

  protected cancelar(): void {
    // A confirmação de saída (se houver dados) é tratada pelo guard de rota.
    this.router.navigate(['/usuarios']);
  }

  /** Abre o diálogo e resolve com a resposta do usuário. */
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
