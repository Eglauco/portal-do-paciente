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

/** Item de menu: um link direto, liberado por uma tela (RBAC). */
interface ItemMenu {
  /** Chave da tela que libera o item (ver AuthService.temTela). */
  readonly tela: string;
  /** Rota de destino do link. */
  readonly rota: string;
  /** Rótulo exibido. */
  readonly label: string;
}

/** Seção do menu: um submenu colapsável (cabeçalho com ícone + itens). */
interface GrupoMenu {
  /** Chave estável (controla o estado aberto/fechado). */
  readonly id: string;
  /** Rótulo da seção (cabeçalho). */
  readonly titulo: string;
  /** Chave do ícone da seção (ver o @switch no template). */
  readonly icone: string;
  /** Itens do submenu (links de texto). */
  readonly itens: readonly ItemMenu[];
}

/**
 * Seções fixas da barra lateral (a ordem aqui é a ordem de exibição). Cada seção
 * é um submenu colapsável: o cabeçalho (ícone + título) abre/fecha os itens, e
 * cada item só aparece quando o usuário tem a tela liberada (temTela). Uma seção
 * some por completo quando nenhum item dela está visível (grupoVisivel). "Meus
 * termos para assinar" (gated por ehProfissional) fica fora daqui, como link solto.
 */
const GRUPOS_MENU: readonly GrupoMenu[] = [
  {
    id: 'dashboards',
    titulo: 'Dashboards',
    icone: 'dashboards',
    itens: [
      { tela: 'DASHBOARD_GERAL', rota: '/dashboards/geral', label: 'Visão geral' },
      { tela: 'DASHBOARD_AGENDAMENTOS', rota: '/dashboards/agendamentos', label: 'Agendamentos' },
      { tela: 'DASHBOARD_CHATS', rota: '/dashboards/chats', label: 'Chats ao vivo' },
      { tela: 'DASHBOARD_SAU', rota: '/dashboards/sau', label: 'SAU' },
      { tela: 'DASHBOARD_NPS', rota: '/dashboards/nps', label: 'NPS' },
    ],
  },
  {
    id: 'atendimento',
    titulo: 'Atendimento',
    icone: 'atendimento',
    itens: [
      { tela: 'AGENDAMENTOS', rota: '/agendas', label: 'Agendamentos' },
      { tela: 'CHATS', rota: '/chats', label: 'Chats ao vivo' },
      { tela: 'SAU', rota: '/sau', label: 'SAU' },
      { tela: 'NPS', rota: '/nps', label: 'NPS' },
    ],
  },
  {
    id: 'prontuario',
    titulo: 'Prontuário',
    icone: 'prontuario',
    itens: [
      { tela: 'PRONTUARIOS', rota: '/prontuarios', label: 'Prontuários' },
      { tela: 'PRONTUARIO_MEDICO', rota: '/prontuario-medico', label: 'Prontuário Médico' },
    ],
  },
  {
    id: 'rede-social',
    titulo: 'Rede social',
    icone: 'rede-social',
    itens: [{ tela: 'POSTAGENS', rota: '/postagens', label: 'Rede Social' }],
  },
  {
    id: 'cadastros',
    titulo: 'Cadastros',
    icone: 'cadastros',
    itens: [
      { tela: 'PACIENTES', rota: '/pacientes', label: 'Pacientes' },
      { tela: 'PROFISSIONAIS', rota: '/profissionais', label: 'Profissionais' },
      { tela: 'ESPECIALIDADES', rota: '/especialidades', label: 'Especialidades' },
      { tela: 'EXAME', rota: '/exames', label: 'Exames' },
      { tela: 'CONSELHOS', rota: '/conselhos', label: 'Conselhos' },
      { tela: 'CONFIGURACAO_AGENDA', rota: '/configuracao-agenda', label: 'Configuração da Agenda' },
      { tela: 'MOTIVOS_FALTA', rota: '/motivos-falta', label: 'Motivos de falta' },
      { tela: 'TIPOS_MANIFESTACAO', rota: '/tipos-manifestacao', label: 'Tipos de Manifestação' },
      { tela: 'CATEGORIAS_NPS', rota: '/categorias-nps', label: 'Categorias de NPS' },
      { tela: 'UNIDADES', rota: '/unidades', label: 'Unidades de Saúde' },
      { tela: 'PRONTUARIOS', rota: '/tipos-documento-prontuario', label: 'Tipos de Documento' },
    ],
  },
  {
    id: 'integracoes',
    titulo: 'Integrações',
    icone: 'integracoes',
    itens: [{ tela: 'SIRESP', rota: '/siresp', label: 'SIRESP' }],
  },
  {
    id: 'sistema',
    titulo: 'Sistema',
    icone: 'sistema',
    itens: [
      { tela: 'USUARIOS', rota: '/usuarios', label: 'Usuários' },
      { tela: 'PERFIS', rota: '/perfis', label: 'Perfis' },
      { tela: 'CONFIGURACOES', rota: '/configuracoes', label: 'Configurações' },
      { tela: 'PROVEDORES_ASSINATURA', rota: '/provedores-assinatura', label: 'Provedores de assinatura' },
      { tela: 'USO_IA', rota: '/uso-ia', label: 'Uso de IA' },
    ],
  },
];

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

  /** Ids das seções abertas (accordion). Começa vazio: tudo fechado no 1º carregamento. */
  protected readonly gruposAbertos = signal<ReadonlySet<string>>(new Set());

  /** Menu recolhido (só ícones). Lido do localStorage no navegador. */
  protected readonly menuRecolhido = signal(false);

  /** Flyout da seção quando o menu está recolhido (posição fixa, à direita do ícone). */
  protected readonly flyout = signal<{ grupo: GrupoMenu; top: number; left: number } | null>(null);
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

  /** Seções do menu (fixas no código). Renderizadas com @for na barra lateral. */
  protected readonly grupos = GRUPOS_MENU;

  /** True se o usuário tem acesso à tela (controla a exibição do item de menu). */
  protected temTela(chave: string): boolean {
    return this.auth.temTela(chave);
  }

  /** True se ao menos um item da seção está liberado (controla o cabeçalho). */
  protected grupoVisivel(grupo: GrupoMenu): boolean {
    return grupo.itens.some((item) => this.temTela(item.tela));
  }

  /** True se o usuário é um profissional de saúde (mostra "Meus termos para assinar"). */
  protected readonly ehProfissional = this.auth.ehProfissional;

  /** True se a seção está aberta (accordion). */
  protected grupoAberto(id: string): boolean {
    return this.gruposAbertos().has(id);
  }

  /** Abre/fecha uma seção (expandido). Replace imutável do Set para o signal reagir. */
  protected alternarGrupo(id: string): void {
    const abertos = new Set(this.gruposAbertos());
    if (abertos.has(id)) abertos.delete(id);
    else abertos.add(id);
    this.gruposAbertos.set(abertos);
  }

  /** Primeira rota liberada da seção (para o clique no menu recolhido). */
  protected primeiraRota(grupo: GrupoMenu): string | null {
    return grupo.itens.find((item) => this.temTela(item.tela))?.rota ?? null;
  }

  /** Recolhe/expande o menu (só ícones) e guarda a escolha no navegador. */
  protected alternarMenu(): void {
    const recolhido = !this.menuRecolhido();
    this.menuRecolhido.set(recolhido);
    this.fecharFlyout();
    try {
      localStorage.setItem(CHAVE_MENU_RECOLHIDO, recolhido ? '1' : '0');
    } catch {
      // sem persistência: vale só para esta sessão.
    }
  }

  // ---------- Flyout da seção (menu recolhido) ----------

  /** Abre o flyout da seção à direita do ícone (só quando recolhido e com itens visíveis). */
  protected abrirFlyout(grupo: GrupoMenu, evento: Event): void {
    if (!this.menuRecolhido() || !this.grupoVisivel(grupo)) return;
    this.cancelarFecharFlyout();
    const alvo = (evento.currentTarget as HTMLElement).getBoundingClientRect();
    this.flyout.set({ grupo, top: alvo.top, left: alvo.right + 8 });
  }

  /** Agenda o fechamento do flyout (dá tempo de mover o mouse do ícone até ele). */
  protected agendarFecharFlyout(): void {
    this.cancelarFecharFlyout();
    this.fecharFlyoutTimer = setTimeout(() => this.flyout.set(null), 160);
  }

  protected cancelarFecharFlyout(): void {
    if (this.fecharFlyoutTimer) {
      clearTimeout(this.fecharFlyoutTimer);
      this.fecharFlyoutTimer = null;
    }
  }

  protected fecharFlyout(): void {
    this.cancelarFecharFlyout();
    this.flyout.set(null);
  }

  /** Clique na seção: expandido abre/fecha o submenu; recolhido navega ao 1º item. */
  protected aoClicarGrupo(grupo: GrupoMenu): void {
    if (this.menuRecolhido()) {
      const rota = this.primeiraRota(grupo);
      if (rota) {
        this.fecharFlyout();
        this.router.navigateByUrl(rota);
      }
      return;
    }
    this.alternarGrupo(grupo.id);
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
    this.fecharFlyout();
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
