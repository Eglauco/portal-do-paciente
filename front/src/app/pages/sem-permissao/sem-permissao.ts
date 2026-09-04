import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

/**
 * Tela exibida quando o usuário tenta acessar (deep-link) uma funcionalidade que
 * o seu perfil não libera. Mostra a mensagem de permissão negada e, quando há
 * alguma tela liberada, um atalho para voltar ao início.
 */
@Component({
  selector: 'app-sem-permissao',
  styleUrl: './sem-permissao.css',
  template: `
    <section class="dash">
      <div class="denied" role="alert">
        <span class="denied__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4.5" y="10.5" width="15" height="10" rx="2" />
            <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
            <path d="M12 14.5v2.5" />
          </svg>
        </span>
        <h1 class="denied__title">Você não tem permissão</h1>
        <p class="denied__msg">Você não tem permissão para usar essa funcionalidade.</p>
        @if (temInicio()) {
          <button type="button" class="btn btn--solid" (click)="voltar()">Voltar ao início</button>
        } @else {
          <p class="denied__hint">Fale com um administrador para liberar o acesso.</p>
        }
      </div>
    </section>
  `,
})
export class SemPermissao {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Há alguma tela liberada para onde voltar (evita botão que voltaria para cá). */
  protected readonly temInicio = computed(() => this.auth.rotaInicial() !== '/sem-permissao');

  protected voltar(): void {
    this.router.navigateByUrl(this.auth.rotaInicial());
  }
}
