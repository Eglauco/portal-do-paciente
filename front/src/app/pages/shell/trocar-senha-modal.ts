import { Component, inject, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';

/** Nova senha deve conferir com a repetição e ser diferente da atual. */
function senhasValidator(group: AbstractControl): ValidationErrors | null {
  const atual = group.get('senhaAtual')?.value ?? '';
  const nova = group.get('novaSenha')?.value ?? '';
  const repetir = group.get('repetir')?.value ?? '';
  const erros: ValidationErrors = {};
  if (nova && repetir && nova !== repetir) {
    erros['naoConfere'] = true;
  }
  if (nova && atual && nova === atual) {
    erros['igualAtual'] = true;
  }
  return Object.keys(erros).length ? erros : null;
}

/**
 * Modal de troca da própria senha (admin). Confere a atual no backend; ao salvar,
 * todas as sessões são invalidadas e o usuário é deslogado para entrar com a nova.
 */
@Component({
  selector: 'app-trocar-senha-modal',
  imports: [ReactiveFormsModule],
  templateUrl: './trocar-senha-modal.html',
})
export class TrocarSenhaModal {
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  readonly fechar = output<void>();

  protected readonly salvando = signal(false);
  protected readonly mostrar = signal(false);
  protected readonly erroServidor = signal<string | null>(null);

  protected readonly form = new FormGroup(
    {
      senhaAtual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      novaSenha: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(6)] }),
      repetir: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: senhasValidator },
  );

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && (c.touched || c.dirty);
  }

  protected get naoConfere(): boolean {
    return this.form.hasError('naoConfere') && !!this.form.get('repetir')?.touched;
  }

  protected get igualAtual(): boolean {
    return this.form.hasError('igualAtual') && !!this.form.get('novaSenha')?.touched;
  }

  protected alternarMostrar(): void {
    this.mostrar.set(!this.mostrar());
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    this.erroServidor.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.auth.trocarSenha(senhaAtual, novaSenha).subscribe({
      next: () => {
        this.toastr.success('Senha alterada. Entre novamente com a nova senha.');
        // Invalida todas as sessões no servidor; aqui desloga e vai para o login.
        this.auth.logout();
      },
      error: (e: { status?: number; error?: { message?: string } }) => {
        this.salvando.set(false);
        // O único 400 possível aqui (o cliente já valida mín. 6, repetição e nova≠atual)
        // é a senha atual incorreta. Usa a mensagem do backend se vier; senão, a específica.
        this.erroServidor.set(
          e?.status === 400
            ? (e.error?.message ?? 'A senha atual está incorreta.')
            : 'Não foi possível alterar a senha. Tente novamente.',
        );
      },
    });
  }
}
