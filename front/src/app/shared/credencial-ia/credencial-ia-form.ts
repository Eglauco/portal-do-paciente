import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { CredencialIaService } from './credencial-ia.service';
import { EscopoIaInput, FormIa, ResultadoTesteIa, StatusIa } from './credencial-ia.model';

/**
 * Formulário (reutilizável) da credencial de IA (Claude/Anthropic) de um escopo (unidade ou padrão do
 * inquilino): API key (write-only) + modelo por frente. Carrega o status, salva e testa a chave.
 *
 * <p>Para UNIDADE a credencial é OPCIONAL: sem chave própria, a unidade usa o padrão do inquilino.
 * O botão "Usar o padrão da clínica" remove a chave própria da unidade.
 */
@Component({
  selector: 'app-credencial-ia-form',
  imports: [ReactiveFormsModule],
  template: `
    @if (carregando()) {
      <div class="state"><span class="spinner" aria-hidden="true"></span><p>Carregando credencial de IA…</p></div>
    } @else {
      <p class="field-hint" style="margin-bottom: 1rem">
        {{ escopo().tipo === 'unidade'
          ? 'Chave de IA (Claude/Anthropic) desta unidade — usada na moderação de comentários, no chat com IA e na análise/resumo do prontuário. Deixe em branco para usar o padrão da clínica.'
          : 'Chave de IA padrão da clínica (Claude/Anthropic) — usada pelas unidades que não têm chave própria. Sem chave, esses recursos de IA ficam indisponíveis.' }}
      </p>

      @if (usandoPadrao()) {
        <div class="ia-padrao" role="status">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          <div>
            <b>Esta unidade usa a chave de IA padrão da clínica.</b>
            <span>Ela não tem chave própria. Preencha abaixo e salve para dar a ela uma chave só dela.</span>
          </div>
        </div>
      }

      <div class="form-field">
        <label class="label" for="ia-key">API key (Anthropic) <span class="req" aria-hidden="true">*</span></label>
        <input id="ia-key" type="password" class="input" [formControl]="form.controls.apiKey"
          [placeholder]="apiKeyPreenchida() ? '•••••••• (mantém a atual)' : 'sk-ant-...'"
          autocomplete="new-password" [class.input--error]="erroApiKey()" />
        <span class="field-hint">
          {{ apiKeyPreenchida() ? 'Já salva (cifrada). Preencha só para trocar.' : 'Guardada cifrada no banco.' }}
        </span>
      </div>

      <div class="form-grid" style="margin-top: 0.9rem">
        <div class="form-field">
          <label class="label" for="ia-mod-moderacao">Modelo — Moderação</label>
          <input id="ia-mod-moderacao" class="input" [formControl]="form.controls.modeloModeracao"
            [placeholder]="padraoModeracao()" autocomplete="off" />
        </div>
        <div class="form-field">
          <label class="label" for="ia-mod-chat">Modelo — Chat</label>
          <input id="ia-mod-chat" class="input" [formControl]="form.controls.modeloChat"
            [placeholder]="padraoChat()" autocomplete="off" />
        </div>
        <div class="form-field">
          <label class="label" for="ia-mod-prontuario">Modelo — Prontuário</label>
          <input id="ia-mod-prontuario" class="input" [formControl]="form.controls.modeloProntuario"
            [placeholder]="padraoProntuario()" autocomplete="off" />
        </div>
      </div>
      <span class="field-hint">Deixe um modelo em branco para usar o padrão da plataforma (o placeholder mostra qual).</span>

      <label class="ia-check">
        <input type="checkbox" [formControl]="form.controls.ativo" />
        <span>Ativo (quando desligado, a IA deste escopo fica indisponível)</span>
      </label>

      @if (escopo().tipo === 'unidade' && configurado()) {
        <div class="ia-padrao-acao">
          @if (confirmandoRemocao()) {
            <span class="ia-padrao-acao__pergunta">
              Remover a chave própria desta unidade? Ela passará a usar o padrão da clínica.
            </span>
            <button type="button" class="btn btn--ghost btn--perigo" (click)="confirmarUsarPadrao()" [disabled]="removendo()">
              {{ removendo() ? 'Removendo…' : 'Sim, usar o padrão' }}
            </button>
            <button type="button" class="btn btn--ghost" (click)="confirmandoRemocao.set(false)" [disabled]="removendo()">
              Cancelar
            </button>
          } @else {
            <button type="button" class="btn btn--ghost" (click)="confirmandoRemocao.set(true)"
              [disabled]="salvando() || testando()">
              Usar a chave padrão da clínica
            </button>
            <span class="field-hint">Limpa os campos e remove a chave própria desta unidade.</span>
          }
        </div>
      }

      @if (resultado(); as r) {
        <div class="ia-teste" [class.ia-teste--ok]="r.ok" [class.ia-teste--erro]="!r.ok" role="status">
          {{ r.mensagem }}
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn btn--ghost" (click)="testar()" [disabled]="testando() || salvando()">
          {{ testando() ? 'Testando…' : 'Testar chave' }}
        </button>
        <button type="button" class="btn btn--solid" (click)="salvar()" [disabled]="salvando() || testando()">
          {{ salvando() ? 'Salvando…' : 'Salvar credencial' }}
        </button>
      </div>
    }
  `,
  styles: [`
    .ia-check { display: flex; align-items: center; gap: 0.5rem; margin: 1rem 0 0.25rem; font-size: 0.9rem; color: var(--ink); }
    .ia-check input { width: 1rem; height: 1rem; }
    .ia-teste { margin-top: 1rem; padding: 0.7rem 0.95rem; border-radius: 0.55rem; font-size: 0.9rem; }
    .ia-teste--ok { background: #e2f4ec; color: #1e6f54; }
    .ia-teste--erro { background: #fdecea; color: #b42318; }
    .ia-padrao { display: flex; gap: 0.6rem; align-items: flex-start; margin: 0 0 1.1rem;
      padding: 0.75rem 0.9rem; border-radius: 0.55rem; background: #e2f4ec; color: #1e6f54; border: 1px solid #bfe4d3; }
    .ia-padrao svg { width: 1.15rem; height: 1.15rem; flex: 0 0 auto; margin-top: 0.1rem; }
    .ia-padrao b { display: block; }
    .ia-padrao span { font-size: 0.88rem; opacity: 0.92; }
    .ia-padrao-acao { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; margin: 0.85rem 0 0.25rem; }
    .ia-padrao-acao__pergunta { font-size: 0.9rem; color: var(--ink); }
    .btn--perigo { color: #b42318; border-color: #f2c4bd; }
  `],
})
export class CredencialIaForm {
  private readonly service = inject(CredencialIaService);
  private readonly toastr = inject(ToastrService);

  readonly escopo = input.required<EscopoIaInput>();

  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly testando = signal(false);
  protected readonly removendo = signal(false);
  protected readonly confirmandoRemocao = signal(false);
  protected readonly apiKeyPreenchida = signal(false);
  /** O escopo tem credencial PRÓPRIA salva? (false p/ unidade = herda o padrão do inquilino). */
  protected readonly configurado = signal(false);
  protected readonly erroApiKey = signal(false);
  protected readonly resultado = signal<ResultadoTesteIa | null>(null);

  protected readonly padraoModeracao = signal('claude-haiku-4-5');
  protected readonly padraoChat = signal('claude-haiku-4-5');
  protected readonly padraoProntuario = signal('claude-opus-5');

  protected readonly usandoPadrao = computed(() => this.escopo().tipo === 'unidade' && !this.configurado());

  protected readonly form = new FormGroup({
    apiKey: new FormControl('', { nonNullable: true }),
    modeloModeracao: new FormControl('', { nonNullable: true }),
    modeloChat: new FormControl('', { nonNullable: true }),
    modeloProntuario: new FormControl('', { nonNullable: true }),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  constructor() {
    effect(() => {
      const escopo = this.escopo();
      this.carregar(escopo);
    });
  }

  private carregar(escopo: EscopoIaInput): void {
    this.carregando.set(true);
    this.resultado.set(null);
    this.confirmandoRemocao.set(false);
    this.erroApiKey.set(false);
    this.service.status(escopo).subscribe({
      next: (s) => {
        this.aplicarStatus(s);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  private aplicarStatus(s: StatusIa): void {
    this.form.reset({
      apiKey: '',
      modeloModeracao: s.modeloModeracao ?? '',
      modeloChat: s.modeloChat ?? '',
      modeloProntuario: s.modeloProntuario ?? '',
      ativo: s.configurado ? s.ativo : true,
    });
    this.apiKeyPreenchida.set(s.apiKeyPreenchida);
    this.configurado.set(s.configurado);
    this.erroApiKey.set(false);
    if (s.modeloModeracaoPadrao) this.padraoModeracao.set(s.modeloModeracaoPadrao);
    if (s.modeloChatPadrao) this.padraoChat.set(s.modeloChatPadrao);
    if (s.modeloProntuarioPadrao) this.padraoProntuario.set(s.modeloProntuarioPadrao);
  }

  private valores(): FormIa {
    const v = this.form.getRawValue();
    return {
      apiKey: v.apiKey, // em branco mantém a atual
      modeloModeracao: v.modeloModeracao.trim(),
      modeloChat: v.modeloChat.trim(),
      modeloProntuario: v.modeloProntuario.trim(),
      ativo: v.ativo,
    };
  }

  protected salvar(): void {
    // API key obrigatória só na PRIMEIRA vez (depois, em branco mantém a atual).
    if (!this.apiKeyPreenchida() && !this.form.controls.apiKey.value.trim()) {
      this.erroApiKey.set(true);
      this.toastr.error('Informe a API key da Anthropic.');
      return;
    }
    this.salvando.set(true);
    this.resultado.set(null);
    this.service.salvar(this.escopo(), this.valores()).subscribe({
      next: (s) => {
        this.apiKeyPreenchida.set(s.apiKeyPreenchida);
        this.configurado.set(s.configurado);
        this.form.controls.apiKey.reset('');
        this.form.markAsPristine();
        this.salvando.set(false);
        this.toastr.success('Credencial de IA salva.');
        this.testar(); // já valida a chave após salvar (feedback imediato)
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(e?.error?.message ?? e?.error?.detail ?? 'Não foi possível salvar a credencial.');
      },
    });
  }

  /** Remove a chave própria da unidade → limpa a tela e volta a usar o padrão da clínica. */
  protected confirmarUsarPadrao(): void {
    this.removendo.set(true);
    this.service.remover(this.escopo()).subscribe({
      next: (s) => {
        this.aplicarStatus(s);
        this.resultado.set(null);
        this.confirmandoRemocao.set(false);
        this.removendo.set(false);
        this.toastr.success('A unidade passou a usar a chave de IA padrão da clínica.');
      },
      error: (e) => {
        this.removendo.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível remover a chave própria da unidade.');
      },
    });
  }

  protected testar(): void {
    this.testando.set(true);
    this.resultado.set(null);
    this.service.testar(this.escopo(), this.valores()).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.testando.set(false);
      },
      error: (e) => {
        this.resultado.set({ ok: false, mensagem: e?.error?.message ?? 'Falha ao testar a chave.' });
        this.testando.set(false);
      },
    });
  }
}
