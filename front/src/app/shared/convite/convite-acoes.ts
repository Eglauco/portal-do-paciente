import { Component, ElementRef, Injector, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { ConvitePaciente, ConviteService, linkWhatsapp } from './convite.service';

let sequencia = 0;

type Modo = 'whatsapp' | 'sms';

/**
 * Botões de convite do app ao lado de um horário: **WhatsApp** (abre o wa.me com a mensagem do convite pronta)
 * e **SMS** (envia o convite pelo provedor de SMS). Só para quem NÃO tem o app; usa os celulares do cadastro
 * do paciente. Com mais de um celular pergunta qual usar (no SMS dá para marcar vários); o SMS sempre pede
 * confirmação. Cada uso vai para o histórico do horário (o backend registra; aqui só emitimos `registrado`).
 *
 * A tela carrega as opções de todos os pacientes de uma vez ({@link ConviteService#opcoes}) e passa a de cada
 * linha em `paciente` (`undefined` = carregando, `null` = indisponível).
 */
@Component({
  selector: 'app-convite-acoes',
  templateUrl: './convite-acoes.html',
  styles: [`
    :host { display: inline-flex; }
    .cv { display: inline-flex; align-items: center; gap: 0.4rem; flex-wrap: nowrap; }
    .cv__btn { display: inline-flex; align-items: center; gap: 0.35rem; white-space: nowrap; }
    .cv__btn[aria-disabled='true'] { opacity: 0.5; cursor: not-allowed; }
    .cv__btn[aria-disabled='true']:hover { border-color: var(--line); background: var(--surface); }
    .cv__ic { width: 1rem; height: 1rem; flex: none; }
    .btn--ghost .cv__ic--wa { stroke: #128c7e; }

    .cv-dlg { width: min(500px, calc(100vw - 2rem)); padding: 0; border: 0; border-radius: 0.8rem; background: var(--surface, #fff); color: var(--ink); box-shadow: 0 16px 48px rgba(0, 0, 0, 0.22); text-align: left; white-space: normal; font-weight: 400; }
    .cv-dlg::backdrop { background: rgba(0, 0, 0, 0.45); }
    .cv-dlg__in { display: flex; flex-direction: column; }
    .cv-dlg__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.25rem 0.4rem; }
    .cv-dlg__title { font-size: 1.1rem; font-weight: 700; margin: 0; }
    .cv-dlg__x { border: 0; background: transparent; font-size: 1.4rem; line-height: 1; color: var(--muted); cursor: pointer; padding: 0; min-width: 2rem; min-height: 2rem; border-radius: 0.4rem; }
    .cv-dlg__body { padding: 0.25rem 1.25rem 1rem; font-size: 0.92rem; line-height: 1.5; }
    .cv-dlg__body p { margin: 0 0 0.6rem; }
    .cv-opts { border: 1px solid var(--line); border-radius: 0.55rem; padding: 0.5rem 0.8rem 0.6rem; margin: 0 0 0.75rem; }
    .cv-opts legend { padding: 0 0.3rem; font-weight: 600; font-size: 0.88rem; }
    .cv-opt { display: flex; align-items: center; gap: 0.55rem; padding: 0.35rem 0; cursor: pointer; font-variant-numeric: tabular-nums; }
    .cv-opt input { width: 1.05rem; height: 1.05rem; margin: 0; accent-color: var(--brand); }
    .cv-nota { font-size: 0.82rem; color: var(--muted); }
    .cv-msg-label { font-weight: 600; font-size: 0.85rem; margin: 0.25rem 0 0.3rem !important; }
    .cv-msg { white-space: pre-wrap; word-break: break-word; padding: 0.6rem 0.8rem; border-radius: 0.5rem; background: color-mix(in srgb, var(--brand) 6%, transparent); border: 1px solid var(--line); font-size: 0.85rem; max-height: 11rem; overflow: auto; }
    .cv-dlg__foot { display: flex; align-items: center; justify-content: flex-end; gap: 0.5rem; padding: 0.75rem 1.25rem 1.1rem; border-top: 1px solid var(--line); }
    .cv-dlg__foot .btn { text-decoration: none; }
  `],
})
export class ConviteAcoes {
  private readonly service = inject(ConviteService);
  private readonly toastr = inject(ToastrService);
  private readonly injector = inject(Injector);

  readonly horarioId = input.required<number>();
  /** Situação do paciente: `undefined` = carregando; `null` = não foi possível carregar. */
  readonly paciente = input<ConvitePaciente | null | undefined>(undefined);
  /** Texto do convite configurado (null = não configurado). */
  readonly texto = input<string | null | undefined>(undefined);
  /** Nome de exibição do paciente (contexto para o leitor de tela e para o diálogo). */
  readonly nome = input('');
  /** Emitido depois de um envio por SMS aceito ou de uma abertura do WhatsApp registrada (para recarregar o histórico). */
  readonly registrado = output<void>();

  protected readonly uid = `cv-${++sequencia}`;
  protected readonly modo = signal<Modo | null>(null);
  protected readonly escolhaWhats = signal<string | null>(null);
  protected readonly escolhaSms = signal<ReadonlySet<string>>(new Set());
  protected readonly enviando = signal(false);
  private readonly dialogo = viewChild<ElementRef<HTMLDialogElement>>('dialogo');

  protected readonly telefones = computed(() => this.paciente()?.telefones ?? []);

  /** Por que os botões estão indisponíveis (vai no `title` e para o leitor de tela); `null` = disponíveis. */
  protected readonly bloqueio = computed<string | null>(() => {
    const p = this.paciente();
    if (p === undefined) return 'Carregando os dados do convite…';
    if (p === null) return 'Não foi possível verificar os dados do convite deste paciente.';
    if (p.temApp) return 'O paciente já tem o app — o convite não é necessário.';
    if (!this.texto()) return 'O texto do convite não está configurado (Sistema › Configurações).';
    if (p.telefones.length === 0) return 'O paciente não tem celular no cadastro.';
    return null;
  });

  protected readonly linkEscolhido = computed(() => {
    const numero = this.escolhaWhats();
    const texto = this.texto();
    return numero && texto ? linkWhatsapp(numero, texto) : null;
  });

  protected abrirWhatsapp(): void {
    if (this.bloqueio()) return;
    const fones = this.telefones();
    if (fones.length === 1) {
      // Um só celular: abre direto (o window.open está no próprio clique, então o navegador não bloqueia).
      window.open(linkWhatsapp(fones[0].numero, this.texto()!), '_blank', 'noopener');
      this.registrarWhatsapp(fones[0].numero);
      return;
    }
    this.escolhaWhats.set(null);
    this.abrirDialogo('whatsapp');
  }

  /** Clique em "Abrir no WhatsApp" no diálogo: o próprio <a> abre a nova aba; aqui só registra e fecha. */
  protected confirmarWhatsapp(): void {
    const numero = this.escolhaWhats();
    if (!numero) return;
    this.registrarWhatsapp(numero);
    this.fechar();
  }

  protected abrirSms(): void {
    if (this.bloqueio()) return;
    const fones = this.telefones();
    // Um só celular já vem marcado; com vários, o atendente escolhe (nenhum pré-marcado).
    this.escolhaSms.set(new Set(fones.length === 1 ? [fones[0].numero] : []));
    this.abrirDialogo('sms');
  }

  protected marcarSms(numero: string, evento: Event): void {
    const marcado = (evento.target as HTMLInputElement).checked;
    this.escolhaSms.update((atual) => {
      const novo = new Set(atual);
      if (marcado) novo.add(numero);
      else novo.delete(numero);
      return novo;
    });
  }

  protected enviarSms(): void {
    const escolhidos = [...this.escolhaSms()];
    if (escolhidos.length === 0 || this.enviando()) return;
    this.enviando.set(true);
    this.service.enviarSms(this.horarioId(), escolhidos).subscribe({
      next: (r) => {
        this.enviando.set(false);
        if (r.enviados.length > 0) {
          this.toastr.success(`Convite enviado por SMS para ${r.enviados.join(', ')}.`);
          this.registrado.emit();
        }
        if (r.falhas.length > 0) {
          this.toastr.error(`Não foi possível enviar o SMS para ${r.falhas.join(', ')}. Tente novamente mais tarde.`);
        }
        // Se nada saiu, mantém o diálogo aberto para tentar de novo sem reabrir.
        if (r.enviados.length > 0) this.fechar();
      },
      error: (e) => {
        this.enviando.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível enviar o SMS de convite.');
      },
    });
  }

  protected fechar(): void {
    if (this.enviando()) return;
    this.dialogo()?.nativeElement.close();
  }

  /** Esc: não deixa fechar no meio de um envio. */
  protected aoCancelar(evento: Event): void {
    if (this.enviando()) evento.preventDefault();
  }

  protected aoFechar(): void {
    this.modo.set(null);
  }

  /** Clique no fundo escurecido (o alvo é o próprio <dialog>; o conteúdo fica num wrapper). */
  protected cliqueNoFundo(evento: MouseEvent): void {
    if (evento.target === this.dialogo()?.nativeElement) this.fechar();
  }

  private abrirDialogo(modo: Modo): void {
    this.modo.set(modo);
    // Abre depois de renderizar o conteúdo, para o `autofocus` da 1ª opção valer. O showModal deixa o fundo
    // inerte, prende o foco no diálogo, fecha no Esc e devolve o foco ao botão que o abriu (tudo nativo).
    afterNextRender(
      () => {
        const d = this.dialogo()?.nativeElement;
        if (d && !d.open) d.showModal();
      },
      { injector: this.injector },
    );
  }

  private registrarWhatsapp(numero: string): void {
    this.service.registrarWhatsapp(this.horarioId(), numero).subscribe({
      next: () => this.registrado.emit(),
      // Registro no histórico é acessório: o WhatsApp já abriu; não incomoda o atendente com erro.
      error: () => undefined,
    });
  }
}
