import { DatePipe } from '@angular/common';
import { Component, afterNextRender, DestroyRef, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ToastrService } from 'ngx-toastr';
import { CoassinaturaService, TermoProfissional } from './coassinatura.service';

/**
 * Tela do PROFISSIONAL logado: lista os termos dos seus atendimentos aguardando a coassinatura dele (após o
 * paciente) e abre a cerimônia do ZapSign EMBUTIDA num iframe (igual ao WebView do app). Ao receber o evento
 * {@code zs-doc-signed} do widget, confere no backend e recarrega — o termo vira ASSINADO (2/2).
 */
@Component({
  selector: 'app-meus-termos-profissional',
  imports: [DatePipe],
  template: `
    <section class="dash">
      <header class="page-head">
        <div>
          <h1 class="page-title">Meus termos para assinar</h1>
          <p class="page-sub">Termos dos seus atendimentos aguardando a sua assinatura (após o paciente).</p>
        </div>
        <button type="button" class="btn btn--ghost" (click)="carregar()" [disabled]="carregando()">Atualizar</button>
      </header>

      @if (carregando()) {
        <div class="state"><span class="spinner" aria-hidden="true"></span><p>Carregando…</p></div>
      } @else if (erro()) {
        <div class="panel aviso">Não foi possível carregar seus termos. Tente novamente.</div>
      } @else if (termos().length === 0) {
        <div class="panel aviso">Nenhum termo aguardando a sua assinatura.</div>
      } @else {
        <div class="lista">
          @for (t of termos(); track t.prontuarioId) {
            <div class="panel cartao">
              <div class="cartao__info">
                <div class="cartao__pac">{{ t.paciente || 'Paciente' }}</div>
                <div class="cartao__meta">
                  @if (t.especialidade) {<span>{{ t.especialidade }}</span> · }<span>{{ t.dataHora | date: 'dd/MM/yyyy HH:mm' }}</span>
                  @if (t.certificado) { · <span class="cartao__cert">certificado digital</span> }
                </div>
                <div class="cartao__termos">
                  @for (nome of t.termos; track nome) {
                    <span class="cartao__termo">{{ nome }}</span>
                  }
                </div>
              </div>
              @if (estaConfirmando(t.prontuarioId)) {
                <span class="cartao__confirmando"><span class="mini-spinner" aria-hidden="true"></span>Confirmando…</span>
              } @else {
                <button type="button" class="btn btn--solid cartao__btn" (click)="abrir(t)" [disabled]="!t.signUrl">
                  {{ t.termos.length > 1 ? 'Assinar ' + t.termos.length + ' termos' : 'Assinar' }}
                </button>
              }
            </div>
          }
        </div>
      }
    </section>

    @if (aguardandoCert() != null) {
      <div class="cert-banner">
        <span>Assine na aba que abriu e depois confira aqui.</span>
        <div class="cert-banner__acoes">
          <button type="button" class="btn btn--ghost" (click)="cancelarCert()">Cancelar</button>
          <button type="button" class="btn btn--solid" (click)="conferirCert()" [disabled]="conferindo()">
            {{ conferindo() ? 'Conferindo…' : 'Já assinei — conferir' }}
          </button>
        </div>
      </div>
    }

    @if (cerimonia(); as url) {
      <div class="cerimonia" role="dialog" aria-modal="true" aria-label="Assinatura do termo">
        <div class="cerimonia__bar">
          <span>Assinatura do termo</span>
          <button type="button" class="btn btn--ghost" (click)="fechar()">Fechar</button>
        </div>
        <iframe class="cerimonia__frame" [src]="url" allow="camera" title="Assinatura do termo"></iframe>
      </div>
    }
  `,
  styles: [
    `
      .aviso { padding: 1.1rem 1.25rem; color: var(--muted); text-align: left; }
      .lista { display: flex; flex-direction: column; gap: 0.75rem; }
      .cartao { display: flex; align-items: center; justify-content: space-between; gap: 1.25rem; padding: 1rem 1.25rem; text-align: left; }
      .cartao__info { min-width: 0; flex: 1; }
      .cartao__pac { font-weight: 700; color: var(--ink); font-size: 1rem; }
      .cartao__meta { font-size: 0.85rem; color: var(--muted); margin-top: 0.15rem; }
      .cartao__cert { color: var(--brand-deep, var(--brand)); font-weight: 600; }
      .cartao__termos { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.6rem; }
      .cartao__termo { font-size: 0.82rem; color: var(--ink); background: color-mix(in srgb, var(--brand) 8%, transparent); border: 1px solid var(--line); border-radius: 0.4rem; padding: 0.15rem 0.5rem; }
      .cartao__btn { flex-shrink: 0; white-space: nowrap; }
      .cartao__confirmando { display: inline-flex; align-items: center; gap: 0.5rem; flex-shrink: 0; white-space: nowrap; color: var(--muted); font-size: 0.9rem; font-weight: 600; }
      .mini-spinner { width: 1rem; height: 1rem; border: 2px solid var(--line); border-top-color: var(--brand); border-radius: 50%; animation: girar 0.7s linear infinite; }
      @keyframes girar { to { transform: rotate(360deg); } }
      @media (max-width: 640px) {
        .cartao { flex-direction: column; align-items: stretch; }
        .cartao__btn { width: 100%; }
        .cartao__confirmando { width: 100%; justify-content: center; }
      }
      .cerimonia { position: fixed; inset: 0; z-index: 1000; background: rgba(0, 0, 0, 0.55); display: flex; flex-direction: column; }
      .cerimonia__bar { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.6rem 1rem; background: var(--surface); color: var(--ink); font-weight: 600; }
      .cerimonia__frame { flex: 1; width: 100%; border: 0; background: #fff; }
      .cert-banner {
        position: fixed; left: 50%; transform: translateX(-50%); bottom: 1.25rem; z-index: 1000;
        display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; justify-content: center;
        max-width: 92vw; padding: 0.85rem 1.1rem; border: 1px solid var(--line); border-radius: 0.8rem;
        background: var(--surface); box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18); color: var(--ink); font-size: 0.9rem;
      }
      .cert-banner__acoes { display: flex; gap: 0.5rem; }
    `,
  ],
})
export class MeusTermosProfissional {
  private readonly service = inject(CoassinaturaService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toastr = inject(ToastrService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly termos = signal<TermoProfissional[]>([]);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  /** Cerimônias (prontuarioId) em "Confirmando…" — assinei, aguardando o provedor concluir e o termo sair da lista. */
  protected readonly confirmando = signal<number[]>([]);
  /** URL segura da cerimônia embutida (null = fechada) — usada no modo assinatura em tela. */
  protected readonly cerimonia = signal<SafeResourceUrl | null>(null);
  /** Prontuário aguardando assinatura por CERTIFICADO (aberta em aba cheia; conferência manual/foco). */
  protected readonly aguardandoCert = signal<number | null>(null);
  protected readonly conferindo = signal(false);
  private cerimoniaProntuarioId: number | null = null;
  /** Poll enquanto a cerimônia EMBUTIDA está aberta (provedores que não postam evento, ex.: Autentique). */
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private readonly aoMensagem = (e: MessageEvent) => this.tratarMensagem(e);
  private readonly aoFoco = () => this.aoVoltarFoco();

  constructor() {
    afterNextRender(() => {
      // O widget do ZapSign posta eventos zs-* para a janela pai (igual ao app).
      window.addEventListener('message', this.aoMensagem);
      // Certificado abre em aba: ao voltar o foco para cá, confere se concluiu.
      window.addEventListener('focus', this.aoFoco);
      this.destroyRef.onDestroy(() => {
        window.removeEventListener('message', this.aoMensagem);
        window.removeEventListener('focus', this.aoFoco);
        this.pararPoll();
      });
      this.carregar();
    });
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.meusTermos().subscribe({
      next: (t) => {
        this.termos.set(t);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Recarrega a lista SEM piscar o estado global de carregando (usado no loop de confirmação). */
  private recarregarSilencioso(): void {
    this.service.meusTermos().subscribe({ next: (t) => this.termos.set(t), error: () => {} });
  }

  /** true se esta cerimônia está no estado "Confirmando…" (assinatura em tela concluída, ou conferência do certificado). */
  protected estaConfirmando(prontuarioId: number): boolean {
    return this.confirmando().includes(prontuarioId)
      || (this.conferindo() && this.aguardandoCert() === prontuarioId);
  }

  protected abrir(t: TermoProfissional): void {
    if (!t.signUrl) {
      this.toastr.error('Link de assinatura indisponível para este termo.');
      return;
    }
    this.cerimoniaProntuarioId = t.prontuarioId;
    if (t.certificado || t.abrirEmAba) {
      // Certificado (A1/A3) ou provedor não-embutível (DocuSign): abre em ABA cheia; confere no retorno do foco.
      window.open(t.signUrl, '_blank', 'noopener');
      this.aguardandoCert.set(t.prontuarioId);
    } else {
      this.cerimonia.set(this.sanitizer.bypassSecurityTrustResourceUrl(t.signUrl));
      this.iniciarPoll(); // detecta a conclusão mesmo sem evento do provedor (ex.: Autentique)
    }
  }

  protected fechar(): void {
    const id = this.cerimoniaProntuarioId;
    this.pararPoll();
    this.cerimonia.set(null);
    this.entrarConfirmando(id); // pode ter assinado — mostra "Confirmando…" e fica conferindo
  }

  /**
   * Confere se a assinatura na aba já concluiu. {@code manual} = disparado pelo botão (dá feedback quando ainda
   * não assinou); no auto-foco fica silencioso quando pendente (evita toast a cada vez que o foco volta).
   */
  protected conferirCert(manual = true): void {
    const id = this.aguardandoCert();
    this.conferindo.set(true);
    this.service.conferir(id ?? -1).subscribe({
      next: (termos) => {
        this.conferindo.set(false);
        const assinou = !termos.some((t) => t.status === 'AGUARDANDO_PROFISSIONAL');
        if (assinou) {
          // Concluiu: encerra o modo certificado.
          this.aguardandoCert.set(null);
          this.toastr.success('Assinatura concluída.');
        } else if (manual) {
          this.toastr.info(
            'Ainda não recebemos a sua assinatura. Se você assina com certificado digital, confirme que o '
              + 'certificado ICP-Brasil (A1/A3) está instalado e válido e tente assinar novamente.',
          );
        }
        this.recarregarSilencioso();
      },
      error: () => {
        this.conferindo.set(false);
        if (manual) {
          this.toastr.error('Não foi possível conferir a assinatura agora. Tente novamente.');
        }
        this.recarregarSilencioso();
      },
    });
  }

  protected cancelarCert(): void {
    this.aguardandoCert.set(null);
    this.carregar();
  }

  /** Ao voltar o foco à janela (retorno da aba do certificado), confere automaticamente (silencioso se pendente). */
  private aoVoltarFoco(): void {
    if (this.aguardandoCert() != null && !this.conferindo()) {
      this.conferirCert(false);
    }
  }

  private tratarMensagem(e: MessageEvent): void {
    const data = typeof e.data === 'string' ? e.data : '';
    if (data.includes('zs-doc-signed') || data.includes('zs-signed-file-ready')) {
      const id = this.cerimoniaProntuarioId;
      this.pararPoll();
      this.cerimonia.set(null);
      this.entrarConfirmando(id);
    }
  }

  /** Enquanto a cerimônia embutida está aberta, confere periodicamente (para provedores que não postam evento). */
  private iniciarPoll(): void {
    this.pararPoll();
    this.pollTimer = setInterval(() => this.conferirCerimoniaAberta(), 5000);
  }

  private pararPoll(): void {
    if (this.pollTimer != null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  /** Tick do poll: se o termo saiu de AGUARDANDO_PROFISSIONAL, a assinatura concluiu → fecha e finaliza. */
  private conferirCerimoniaAberta(): void {
    const id = this.cerimoniaProntuarioId;
    if (id == null || this.cerimonia() == null) {
      this.pararPoll();
      return;
    }
    this.service.conferir(id).subscribe({
      next: (termos) => {
        if (!termos.some((t) => t.status === 'AGUARDANDO_PROFISSIONAL')) {
          this.pararPoll();
          this.cerimonia.set(null);
          this.sairConfirmando(id);
          this.recarregarSilencioso();
          this.toastr.success('Assinatura concluída.');
        }
      },
      error: () => {},
    });
  }

  /**
   * Entra no estado "Confirmando…" da cerimônia (igual ao app): esconde o botão e fica conferindo no provedor
   * até o documento concluir (o termo some da lista). Cobre o lag do provedor entre "assinei" e "concluído".
   */
  private entrarConfirmando(id: number | null): void {
    if (id == null) {
      this.carregar();
      return;
    }
    if (!this.confirmando().includes(id)) {
      this.confirmando.update((ids) => [...ids, id]);
    }
    this.aguardarConfirmacao(id, 10); // ~30s (10 tentativas x 3s) cobrindo o lag do provedor
  }

  /** Loop de conferência: pergunta ao provedor se concluiu; se ainda pendente, tenta de novo até esgotar. */
  private aguardarConfirmacao(id: number, restantes: number): void {
    this.service.conferir(id).subscribe({
      next: (termos) => {
        const aindaPendente = termos.some((t) => t.status === 'AGUARDANDO_PROFISSIONAL');
        this.recarregarSilencioso();
        if (!aindaPendente) {
          this.sairConfirmando(id);
          this.toastr.success('Assinatura concluída.');
        } else if (restantes > 0) {
          setTimeout(() => this.aguardarConfirmacao(id, restantes - 1), 3000);
        } else {
          this.sairConfirmando(id); // esgotou: volta o botão (provedor ainda não concluiu)
        }
      },
      error: () => {
        if (restantes > 0) {
          setTimeout(() => this.aguardarConfirmacao(id, restantes - 1), 3000);
        } else {
          this.sairConfirmando(id);
          this.recarregarSilencioso();
        }
      },
    });
  }

  private sairConfirmando(id: number): void {
    this.confirmando.update((ids) => ids.filter((x) => x !== id));
  }
}
