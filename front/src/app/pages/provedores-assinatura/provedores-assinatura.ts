import { Component, afterNextRender, inject, signal } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import {
  CredenciaisRequest,
  ProvedoresAssinaturaService,
  ProvedoresResponse,
  ProvedorStatus,
  ResultadoTeste,
} from './provedores-assinatura.service';

/** Valores editáveis de um provedor no formulário (token/webhook em branco = manter). */
interface Edicao {
  token: string;
  webhook: string;
  ambiente: string;
  urlSandbox: string;
  urlProducao: string;
}

/**
 * Tela "Provedores de assinatura": o cliente cadastra as credenciais (token + webhook) e o ambiente de cada
 * provedor, escolhe o provedor ATIVO e testa a conexão — tudo self-service. Os segredos são WRITE-ONLY: a tela
 * nunca mostra o valor (só se está "preenchido"); deixar o campo em branco mantém o segredo atual.
 */
@Component({
  selector: 'app-provedores-assinatura',
  template: `
    <section class="dash">
      <header class="page-head">
        <div>
          <h1 class="page-title">Provedores de assinatura</h1>
          <p class="page-sub">
            Cadastre as credenciais de cada provedor, escolha o ambiente e o provedor ativo, e teste a conexão.
            Os segredos ficam cifrados e nunca são exibidos de volta.
          </p>
        </div>
        <button type="button" class="btn btn--ghost" (click)="carregar()" [disabled]="carregando()">Atualizar</button>
      </header>

      <div class="panel ativo">
        <label class="ativo__label" for="provedor-ativo">Provedor ativo</label>
        <select
          id="provedor-ativo"
          class="campo campo--ativo"
          [value]="ativo()"
          (change)="mudarAtivo($any($event.target).value)"
          [disabled]="trocandoAtivo()"
        >
          @for (op of OPCOES_ATIVO; track op.id) {
            <option [value]="op.id">{{ op.nome }}</option>
          }
        </select>
        <span class="ativo__hint">É o provedor que o app/front usa para assinar agora.</span>
      </div>

      @if (carregando() && !estado()) {
        <div class="state"><span class="spinner" aria-hidden="true"></span><p>Carregando…</p></div>
      } @else if (erro()) {
        <div class="panel aviso">Não foi possível carregar os provedores. Tente novamente.</div>
      } @else {
        <div class="cards">
          @for (p of estado()?.provedores ?? []; track p.id) {
            <div class="panel card">
              <div class="card__head">
                <h2 class="card__nome">{{ p.nome }}</h2>
                @if (p.id === ativo()) {
                  <span class="tag tag--ativo">Ativo</span>
                }
                @if (p.disponivel) {
                  <span class="tag tag--ok">Configurado</span>
                } @else {
                  <span class="tag tag--off">Sem credenciais</span>
                }
              </div>

              <label class="campo-label" [attr.for]="'amb-' + p.id">Ambiente ativo</label>
              <select
                [id]="'amb-' + p.id"
                class="campo"
                [value]="edicao(p.id).ambiente"
                (change)="setEdicao(p.id, 'ambiente', $any($event.target).value)"
              >
                <option value="SANDBOX">Sandbox (testes)</option>
                <option value="PRODUCAO">Produção</option>
              </select>
              <span class="campo-hint">Define qual das URLs abaixo é usada agora.</span>

              <label class="campo-label" [attr.for]="'url-sb-' + p.id">URL de sandbox</label>
              <input
                [id]="'url-sb-' + p.id"
                class="campo"
                type="url"
                inputmode="url"
                autocomplete="off"
                spellcheck="false"
                placeholder="https://…"
                [value]="edicao(p.id).urlSandbox"
                (input)="setEdicao(p.id, 'urlSandbox', $any($event.target).value)"
              />

              <label class="campo-label" [attr.for]="'url-pr-' + p.id">URL de produção</label>
              <input
                [id]="'url-pr-' + p.id"
                class="campo"
                type="url"
                inputmode="url"
                autocomplete="off"
                spellcheck="false"
                placeholder="https://…"
                [value]="edicao(p.id).urlProducao"
                (input)="setEdicao(p.id, 'urlProducao', $any($event.target).value)"
              />

              <label class="campo-label" [attr.for]="'tok-' + p.id">Token de API</label>
              <input
                [id]="'tok-' + p.id"
                class="campo"
                type="password"
                autocomplete="new-password"
                [placeholder]="p.tokenPreenchido ? '•••••••• (preenchido — em branco mantém)' : 'Cole o token de API'"
                [value]="edicao(p.id).token"
                (input)="setEdicao(p.id, 'token', $any($event.target).value)"
              />

              <label class="campo-label" [attr.for]="'wh-' + p.id">Segredo do webhook</label>
              <input
                [id]="'wh-' + p.id"
                class="campo"
                type="password"
                autocomplete="new-password"
                [placeholder]="p.webhookPreenchido ? '•••••••• (preenchido — em branco mantém)' : 'Cole o segredo do webhook'"
                [value]="edicao(p.id).webhook"
                (input)="setEdicao(p.id, 'webhook', $any($event.target).value)"
              />

              <div class="card__acoes">
                <button
                  type="button"
                  class="btn btn--ghost"
                  (click)="testar(p.id)"
                  [disabled]="!podeTestar(p)"
                  [title]="podeTestar(p) ? '' : 'Informe o token de API para testar a conexão.'"
                >
                  {{ testando() === p.id ? 'Testando…' : 'Testar conexão' }}
                </button>
                <button type="button" class="btn btn--solid" (click)="salvar(p.id)" [disabled]="salvando() === p.id">
                  {{ salvando() === p.id ? 'Salvando…' : 'Salvar' }}
                </button>
              </div>

              @if (testes()[p.id]; as t) {
                <p class="teste" [class.teste--ok]="t.ok" [class.teste--erro]="!t.ok">
                  {{ t.ok ? '✓ ' : '✗ ' }}{{ t.mensagem }}
                </p>
              }
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: [
    `
      .ativo { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; padding: 0.9rem 1.25rem; margin-bottom: 1rem; }
      .ativo__label { font-weight: 700; color: var(--ink); }
      .ativo__hint { font-size: 0.82rem; color: var(--muted); }
      .campo--ativo { max-width: 240px; }
      .aviso { padding: 1.1rem 1.25rem; color: var(--muted); }
      .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1rem; }
      .card { padding: 1.1rem 1.25rem; display: flex; flex-direction: column; }
      .card__head { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.3rem; flex-wrap: wrap; }
      .card__nome { font-size: 1.05rem; font-weight: 700; color: var(--ink); margin: 0; }
      .tag { font-size: 0.72rem; font-weight: 700; padding: 0.1rem 0.5rem; border-radius: 0.4rem; border: 1px solid var(--line); color: var(--muted); }
      .tag--ativo { color: var(--brand-deep, var(--brand)); border-color: var(--brand); background: color-mix(in srgb, var(--brand) 12%, transparent); }
      .tag--ok { color: #0a7d3f; border-color: #bfe6cf; background: color-mix(in srgb, #12b76a 14%, transparent); }
      .tag--off { color: var(--muted); }
      .campo-label { font-size: 0.8rem; font-weight: 600; color: var(--muted); margin-top: 0.55rem; margin-bottom: 0.2rem; }
      .campo-hint { font-size: 0.75rem; color: var(--muted); margin-top: 0.2rem; }
      .campo { width: 100%; padding: 0.5rem 0.65rem; border: 1px solid var(--line); border-radius: 0.5rem; background: var(--surface, #fff); color: var(--ink); font-size: 0.92rem; }
      .campo:focus { outline: none; border-color: var(--brand); }
      .card__acoes { display: flex; gap: 0.5rem; margin-top: 0.85rem; }
      .card__acoes .btn { flex: 1; }
      .btn:disabled { opacity: 0.5; cursor: not-allowed; }
      .teste { font-size: 0.85rem; margin: 0.6rem 0 0; font-weight: 600; }
      .teste--ok { color: #0a7d3f; }
      .teste--erro { color: #b42318; }
    `,
  ],
})
export class ProvedoresAssinatura {
  private readonly service = inject(ProvedoresAssinaturaService);
  private readonly toastr = inject(ToastrService);

  /** Opções do seletor de provedor ativo (inclui DocuSign, cujas credenciais ficam na fase 2 via ambiente). */
  protected readonly OPCOES_ATIVO = [
    { id: 'ZAPSIGN', nome: 'ZapSign' },
    { id: 'AUTENTIQUE', nome: 'Autentique' },
    { id: 'CLICKSIGN', nome: 'Clicksign' },
    { id: 'DOCUSIGN', nome: 'DocuSign' },
  ];

  protected readonly estado = signal<ProvedoresResponse | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly ativo = signal('');
  protected readonly trocandoAtivo = signal(false);
  protected readonly salvando = signal<string | null>(null);
  protected readonly testando = signal<string | null>(null);
  protected readonly edicoes = signal<Record<string, Edicao>>({});
  protected readonly testes = signal<Record<string, ResultadoTeste | null>>({});

  constructor() {
    // Só carrega no navegador: no SSR/prerender não há token e o GET daria 401 → o interceptor
    // redireciona p/ /login, que o prerender "congela" na página (quebrava o F5 nesta rota).
    afterNextRender(() => this.carregar());
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.listar().subscribe({
      next: (r) => {
        this.aplicar(r);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Aplica a resposta: atualiza o estado, o ativo e reseta as edições (token/webhook voltam a vazio = manter). */
  private aplicar(r: ProvedoresResponse): void {
    this.estado.set(r);
    this.ativo.set(r.ativo);
    const edits: Record<string, Edicao> = {};
    for (const p of r.provedores) {
      edits[p.id] = {
        token: '',
        webhook: '',
        ambiente: p.ambiente || 'SANDBOX',
        urlSandbox: p.urlSandbox ?? '',
        urlProducao: p.urlProducao ?? '',
      };
    }
    this.edicoes.set(edits);
  }

  protected edicao(id: string): Edicao {
    return this.edicoes()[id] ?? { token: '', webhook: '', ambiente: 'SANDBOX', urlSandbox: '', urlProducao: '' };
  }

  protected setEdicao(id: string, campo: keyof Edicao, valor: string): void {
    this.edicoes.update((e) => ({ ...e, [id]: { ...this.edicao(id), [campo]: valor } }));
  }

  /** Só dá para testar quando há token para usar: já salvo no banco OU digitado no formulário. */
  protected podeTestar(p: ProvedorStatus): boolean {
    if (this.testando() === p.id || this.salvando() === p.id) return false;
    return p.tokenPreenchido || this.edicao(p.id).token.trim() !== '';
  }

  /** aoConcluir roda só em caso de sucesso (usado pelo "Testar" para salvar antes de testar). */
  protected salvar(id: string, aoConcluir?: () => void): void {
    const e = this.edicao(id);
    const req: CredenciaisRequest = {
      token: e.token,
      webhookSecret: e.webhook,
      ambiente: e.ambiente,
      urlSandbox: e.urlSandbox,
      urlProducao: e.urlProducao,
    };
    this.salvando.set(id);
    this.service.salvar(id, req).subscribe({
      next: (r) => {
        this.salvando.set(null);
        this.aplicar(r); // limpa os campos de segredo (ficam como "preenchido")
        this.toastr.success('Credenciais salvas.');
        aoConcluir?.();
      },
      error: (err) => {
        this.salvando.set(null);
        this.toastr.error(err?.error?.message ?? 'Não foi possível salvar.');
      },
    });
  }

  protected testar(id: string): void {
    const e = this.edicao(id);
    // O backend testa o que está SALVO no banco. Se há credencial digitada ainda não salva,
    // salva primeiro e só então testa — assim o teste reflete o que o usuário acabou de informar.
    if (e.token.trim() !== '' || e.webhook.trim() !== '') {
      this.salvar(id, () => this.executarTeste(id));
    } else {
      this.executarTeste(id);
    }
  }

  private executarTeste(id: string): void {
    this.testando.set(id);
    this.service.testar(id).subscribe({
      next: (res) => {
        this.testando.set(null);
        this.testes.update((t) => ({ ...t, [id]: res }));
        if (res.ok) {
          this.toastr.success('Conexão OK.');
        } else {
          this.toastr.error('Falha no teste de conexão.');
        }
      },
      error: () => {
        this.testando.set(null);
        this.testes.update((t) => ({ ...t, [id]: { ok: false, mensagem: 'Erro ao testar a conexão.' } }));
      },
    });
  }

  protected mudarAtivo(valor: string): void {
    if (!valor || valor === this.ativo()) {
      return;
    }
    const anterior = this.ativo();
    this.ativo.set(valor);
    this.trocandoAtivo.set(true);
    this.service.definirAtivo(valor).subscribe({
      next: (r) => {
        this.trocandoAtivo.set(false);
        this.aplicar(r);
        this.toastr.success('Provedor ativo atualizado.');
      },
      error: () => {
        this.trocandoAtivo.set(false);
        this.ativo.set(anterior); // reverte o seletor
        this.toastr.error('Não foi possível trocar o provedor ativo.');
      },
    });
  }
}
