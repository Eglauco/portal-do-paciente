import { afterNextRender, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
})
export class Login {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  constructor() {
    // Quem já está autenticado não precisa ver o login.
    afterNextRender(() => {
      if (this.auth.token()) this.router.navigate(['/inicio']);
    });
  }

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly remember = signal(false);
  protected readonly entrando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (this.entrando()) return;

    const email = this.email().trim();
    const senha = this.password();
    if (!email || !senha) {
      this.erro.set('Informe e-mail e senha.');
      return;
    }

    this.entrando.set(true);
    this.erro.set(null);
    this.auth.login(email, senha, this.remember()).subscribe({
      next: () => this.router.navigate(['/inicio']),
      error: (e) => {
        this.entrando.set(false);
        this.erro.set(
          e?.status === 401
            ? 'E-mail ou senha inválidos.'
            : 'Não foi possível entrar. Verifique sua conexão e tente novamente.',
        );
      },
    });
  }

  protected updateEmail(event: Event): void {
    this.email.set((event.target as HTMLInputElement).value);
  }

  protected updatePassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
  }

  protected updateRemember(event: Event): void {
    this.remember.set((event.target as HTMLInputElement).checked);
  }
}
