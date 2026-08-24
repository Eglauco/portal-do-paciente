import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
})
export class Login {
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly remember = signal(false);

  protected togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    // Sem regra de negócio ainda — segue direto para a Home administrativa.
    this.router.navigate(['/inicio']);
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
