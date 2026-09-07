import { Component, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, UnidadeRef } from '../../core/auth.service';
import { TrocarSenhaModal } from './trocar-senha-modal';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TrocarSenhaModal],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;
  protected readonly unidadeNome = this.auth.unidadeNome;
  protected readonly unidadeAtualId = this.auth.unidadeId;
  /** Unidades que o perfil do usuário libera (usadas no seletor de unidade). */
  protected readonly unidades = this.auth.unidadesAcessiveis;

  /**
   * Só renderiza a navegação dependente da sessão no navegador. No servidor a
   * sessão vem do localStorage (indisponível), então o menu ficaria diferente do
   * cliente e quebraria a hidratação — por isso o chrome autenticado é adiado.
   */
  protected readonly pronto = signal(false);

  /** Submenu "Dashboard" (aberto por padrão para ser descoberto). */
  protected readonly menuDashAberto = signal(true);

  protected readonly menuUnidadeAberto = signal(false);
  protected readonly trocandoUnidade = signal(false);

  /** Menu do usuário (nome no topo) — abriga "Trocar a senha" e "Sair". */
  protected readonly menuUsuarioAberto = signal(false);
  /** Modal de troca de senha. */
  protected readonly modalSenhaAberto = signal(false);

  constructor() {
    afterNextRender(() => {
      this.pronto.set(true);
      // Recarrega telas/unidades do backend (reflete perfil alterado / sessão antiga sem esses campos).
      // Não redireciona a tela de "sem permissão": quem foi barrado deve permanecer nela.
      this.auth.sincronizar().subscribe({ error: () => {} });
    });
  }

  /** True se o usuário tem acesso à tela (controla a exibição do item de menu). */
  protected temTela(chave: string): boolean {
    return this.auth.temTela(chave);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase() || 'US';
  }

  protected abrirMenuUnidade(): void {
    this.menuUnidadeAberto.set(!this.menuUnidadeAberto());
  }

  protected fecharMenuUnidade(): void {
    this.menuUnidadeAberto.set(false);
  }

  protected selecionarUnidade(unidade: UnidadeRef): void {
    if (this.trocandoUnidade() || unidade.id == null) return;
    if (unidade.id === this.unidadeAtualId()) {
      this.fecharMenuUnidade();
      return;
    }
    this.trocandoUnidade.set(true);
    this.auth.trocarUnidade(unidade.id).subscribe({
      // Recarrega a tela por completo para refletir a nova unidade em todos os filtros.
      next: () => window.location.reload(),
      error: () => this.trocandoUnidade.set(false),
    });
  }

  protected abrirMenuUsuario(): void {
    this.menuUsuarioAberto.set(!this.menuUsuarioAberto());
  }

  protected fecharMenuUsuario(): void {
    this.menuUsuarioAberto.set(false);
  }

  protected abrirTrocarSenha(): void {
    this.fecharMenuUsuario();
    this.modalSenhaAberto.set(true);
  }

  protected fecharTrocarSenha(): void {
    this.modalSenhaAberto.set(false);
  }

  protected logout(): void {
    this.fecharMenuUsuario();
    this.auth.logout();
  }
}
