import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase() || 'US';
  }

  protected logout(): void {
    this.auth.logout();
  }
}
