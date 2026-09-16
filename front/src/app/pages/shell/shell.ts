import { Component, afterNextRender, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, UnidadeRef } from '../../core/auth.service';
import { MarcaService } from '../../core/marca.service';
import { NotificacaoAdmin, NotificacaoService } from '../../core/notificacao.service';
import { BuscaFuncionalidades } from '../../shared/busca-funcionalidades/busca-funcionalidades';
import { RailTooltip } from '../../shared/rail-tooltip.directive';
import { TrocarSenhaModal } from './trocar-senha-modal';

/** Chave do localStorage que guarda se o menu está recolhido. */
const CHAVE_MENU_RECOLHIDO = 'pop.menu.recolhido';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TrocarSenhaModal, BuscaFuncionalidades, RailTooltip],
  templateUrl: './shell.html',
  host: { '(document:keydown.escape)': 'aoEscape()' },
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly notif = inject(NotificacaoService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  /** Nome da plataforma (white-label) para o rodapé da sidebar. */
  protected readonly marca = inject(MarcaService);

  protected readonly usuario = this.auth.usuario;
  /** Contagem e lista do sino de notificações. */
  protected readonly naoLidas = this.notif.naoLidas;
  protected readonly notificacoes = this.notif.itens;
  /** Botão do sino — para devolver o foco a ele ao fechar com Esc (acessibilidade). */
  private readonly sinoBtn = viewChild<ElementRef<HTMLButtonElement>>('sinoBtn');
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

  /** Menu recolhido (só ícones). Lido do localStorage no navegador. */
  protected readonly menuRecolhido = signal(false);

  /** Flyout do grupo Dashboard quando recolhido (posição fixa, à direita do ícone). */
  protected readonly flyoutDash = signal<{ top: number; left: number } | null>(null);
  private fecharFlyoutTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly menuUnidadeAberto = signal(false);
  protected readonly trocandoUnidade = signal(false);

  /** Menu do usuário (nome no topo) — abriga "Trocar a senha" e "Sair". */
  protected readonly menuUsuarioAberto = signal(false);
  /** Modal de troca de senha. */
  protected readonly modalSenhaAberto = signal(false);

  /** Sino de notificações (dropdown). */
  protected readonly menuNotifAberto = signal(false);

  constructor() {
    afterNextRender(() => {
      this.pronto.set(true);
      // Estado do menu (recolhido) guardado por navegador.
      try {
        this.menuRecolhido.set(localStorage.getItem(CHAVE_MENU_RECOLHIDO) === '1');
      } catch {
        // localStorage indisponível (aba privada, bloqueado): mantém expandido.
      }
      // Recarrega telas/unidades do backend (reflete perfil alterado / sessão antiga sem esses campos).
      // Não redireciona a tela de "sem permissão": quem foi barrado deve permanecer nela.
      this.auth.sincronizar().subscribe({ error: () => {} });
      // Sino: contagem inicial + polling leve (a cada 45s). Só no navegador.
      this.notif.atualizarContagem();
      const timer = setInterval(() => this.notif.atualizarContagem(), 45_000);
      this.destroyRef.onDestroy(() => clearInterval(timer));
    });
    this.destroyRef.onDestroy(() => this.cancelarFecharFlyout());
  }

  /** True se o usuário tem acesso à tela (controla a exibição do item de menu). */
  protected temTela(chave: string): boolean {
    return this.auth.temTela(chave);
  }

  /** True se o usuário pode ver ao menos um dashboard (controla o grupo "Dashboard"). */
  protected temAlgumDashboard(): boolean {
    return (
      this.temTela('DASHBOARD_GERAL') ||
      this.temTela('DASHBOARD_AGENDAMENTOS') ||
      this.temTela('DASHBOARD_CHATS') ||
      this.temTela('DASHBOARD_SAU') ||
      this.temTela('DASHBOARD_NPS')
    );
  }

  /** Recolhe/expande o menu (só ícones) e guarda a escolha no navegador. */
  protected alternarMenu(): void {
    const recolhido = !this.menuRecolhido();
    this.menuRecolhido.set(recolhido);
    this.fecharFlyoutDash();
    try {
      localStorage.setItem(CHAVE_MENU_RECOLHIDO, recolhido ? '1' : '0');
    } catch {
      // sem persistência: vale só para esta sessão.
    }
  }

  // ---------- Flyout do Dashboard (menu recolhido) ----------

  /** Abre o flyout do Dashboard à direita do ícone (só quando recolhido). */
  protected abrirFlyoutDash(evento: Event): void {
    if (!this.menuRecolhido() || !this.temAlgumDashboard()) return;
    this.cancelarFecharFlyout();
    const alvo = (evento.currentTarget as HTMLElement).getBoundingClientRect();
    this.flyoutDash.set({ top: alvo.top, left: alvo.right + 8 });
  }

  /** Agenda o fechamento do flyout (dá tempo de mover o mouse do ícone até ele). */
  protected agendarFecharFlyout(): void {
    this.cancelarFecharFlyout();
    this.fecharFlyoutTimer = setTimeout(() => this.flyoutDash.set(null), 160);
  }

  protected cancelarFecharFlyout(): void {
    if (this.fecharFlyoutTimer) {
      clearTimeout(this.fecharFlyoutTimer);
      this.fecharFlyoutTimer = null;
    }
  }

  protected fecharFlyoutDash(): void {
    this.cancelarFecharFlyout();
    this.flyoutDash.set(null);
  }

  /** Clique no Dashboard: expandido alterna o submenu; recolhido navega ao 1º dashboard. */
  protected aoClicarDashboard(): void {
    if (this.menuRecolhido()) {
      const rota = this.primeiraRotaDashboard();
      if (rota) {
        this.fecharFlyoutDash();
        this.router.navigateByUrl(rota);
      }
      return;
    }
    this.menuDashAberto.set(!this.menuDashAberto());
  }

  /** Primeira rota de dashboard que o usuário tem acesso (para o clique no menu recolhido). */
  protected primeiraRotaDashboard(): string | null {
    if (this.temTela('DASHBOARD_GERAL')) return '/dashboards/geral';
    if (this.temTela('DASHBOARD_AGENDAMENTOS')) return '/dashboards/agendamentos';
    if (this.temTela('DASHBOARD_CHATS')) return '/dashboards/chats';
    if (this.temTela('DASHBOARD_SAU')) return '/dashboards/sau';
    if (this.temTela('DASHBOARD_NPS')) return '/dashboards/nps';
    return null;
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase() || 'US';
  }

  protected abrirMenuUnidade(): void {
    const abrir = !this.menuUnidadeAberto();
    this.menuUnidadeAberto.set(abrir);
    if (abrir) this.fecharNotif();
  }

  protected fecharMenuUnidade(): void {
    this.menuUnidadeAberto.set(false);
  }

  // ---------- Sino de notificações ----------

  /** Abre/fecha o sino; ao abrir, carrega a lista e fecha os outros menus. */
  protected abrirNotif(): void {
    const abrir = !this.menuNotifAberto();
    this.menuNotifAberto.set(abrir);
    if (abrir) {
      this.fecharMenuUsuario();
      this.fecharMenuUnidade();
      this.notif.carregar();
    }
  }

  protected fecharNotif(): void {
    this.menuNotifAberto.set(false);
  }

  /** Clique num item: marca como lida e navega até a tela correspondente. */
  protected abrirNotificacao(n: NotificacaoAdmin): void {
    this.fecharNotif();
    this.notif.marcarLida(n);
    this.router.navigateByUrl(n.rota);
  }

  protected marcarTodasNotif(): void {
    this.notif.marcarTodasLidas();
  }

  /** Fecha qualquer dropdown aberto (tecla Esc); devolve o foco ao sino se ele estava aberto. */
  protected aoEscape(): void {
    const notifEstava = this.menuNotifAberto();
    this.fecharNotif();
    this.fecharMenuUsuario();
    this.fecharMenuUnidade();
    this.fecharFlyoutDash();
    if (notifEstava) {
      this.sinoBtn()?.nativeElement.focus();
    }
  }

  /** Tempo relativo curto para o item do sino (ex.: "há 5 min"). */
  protected quando(iso: string): string {
    const min = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (min < 1) return 'agora';
    if (min < 60) return `há ${min} min`;
    const h = Math.floor(min / 60);
    if (h < 24) return `há ${h} h`;
    return `há ${Math.floor(h / 24)} d`;
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
    const abrir = !this.menuUsuarioAberto();
    this.menuUsuarioAberto.set(abrir);
    if (abrir) this.fecharNotif();
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
    this.notif.limpar();
    this.auth.logout();
  }
}
