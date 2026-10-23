import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { ModeloIa } from '../modelo-ia/modelo-ia.model';
import { ModeloIaService } from '../modelo-ia/modelo-ia.service';
import { CredencialIaService } from './credencial-ia.service';
import { EscopoIaInput, FormIa, ResultadoTesteIa, StatusIa } from './credencial-ia.model';

/**
 * Formulário (reutilizável) da credencial de IA de um escopo (unidade ou padrão do inquilino):
 * PROVEDOR + API key (write-only) + Base URL opcional + modelo por frente. Carrega o status, salva e testa.
 *
 * <p>O provedor (Anthropic/Claude e, no futuro, OpenAI/Gemini/Azure) vem de um SELECT alimentado por
 * {@code GET /configuracao-ia/provedores} (só os suportados e com modelo ativo). Os 3 modelos são
 * escolhidos num SELECT alimentado pelo cadastro "Modelos de IA" FILTRADO pelo provedor escolhido
 * (sem erro de digitação) e são OBRIGATÓRIOS — o modelo tem preço cadastrado, então o custo sempre computa.
 * A unidade pode ter um provedor e o inquilino outro — não precisam ser iguais.
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
          ? 'Credencial de IA desta unidade — usada na moderação de comentários, no chat com IA e na análise/resumo do prontuário. Deixe em branco para usar o padrão da clínica.'
          : 'Credencial de IA padrão da clínica — usada pelas unidades que não têm credencial própria. Sem ela, esses recursos de IA ficam indisponíveis.' }}
      </p>

      @if (usandoPadrao()) {
        <div class="ia-padrao" role="status">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          <div>
            <b>Esta unidade usa a credencial de IA padrão da clínica.</b>
            <span>Ela não tem credencial própria. Preencha abaixo e salve para dar a ela uma credencial só dela.</span>
          </div>
        </div>
      }

      <div class="form-field">
        <label class="label" for="ia-provedor">Provedor de IA <span class="req" aria-hidden="true">*</span></label>
        <select id="ia-provedor" class="input" [formControl]="form.controls.provedor">
          @for (p of opcoesProvedor(); track p.id) {
            <option [value]="p.id">{{ p.rotulo }}</option>
          }
        </select>
        <span class="field-hint">Cada escopo escolhe o seu — a unidade pode usar um provedor e a clínica outro.</span>
      </div>

      <div class="form-field" style="margin-top: 0.9rem">
        <label class="label" for="ia-key">API key ({{ rotuloProvedor(form.controls.provedor.value) }}) <span class="req" aria-hidden="true">*</span></label>
        <input id="ia-key" type="password" class="input" [formControl]="form.controls.apiKey"
          [placeholder]="apiKeyPreenchida() ? '•••••••• (mantém a atual)' : placeholderChave()"
          autocomplete="new-password" [class.input--error]="erroApiKey()" />
        <span class="field-hint">
          {{ apiKeyPreenchida() ? 'Já salva (cifrada). Preencha só para trocar.' : 'Guardada cifrada no banco.' }}
        </span>
      </div>

      <div class="form-field" style="margin-top: 0.9rem">
        <label class="label" for="ia-base-url">Base URL (opcional)</label>
        <input id="ia-base-url" type="url" class="input" [formControl]="form.controls.baseUrl"
          placeholder="Deixe em branco para o padrão do provedor" autocomplete="off" />
        <span class="field-hint">Só preencha para apontar a um endpoint próprio/compatível (ex.: Azure, gateway, self-hosted).</span>
      </div>

      @if (semModelos()) {
        <div class="ia-sem-modelos" role="alert">
          Nenhum modelo de IA ativo cadastrado para <b>{{ rotuloProvedor(form.controls.provedor.value) }}</b>.
          Cadastre as versões (com preço) em <b>Modelos de IA</b> antes de salvar a credencial.
        </div>
      }

      <div class="form-grid" style="margin-top: 0.9rem">
        <div class="form-field">
          <label class="label" for="ia-mod-moderacao">Modelo — Moderação <span class="req" aria-hidden="true">*</span></label>
          <select id="ia-mod-moderacao" class="input" [formControl]="form.controls.modeloModeracao"
            [class.input--error]="invalido('modeloModeracao')">
            <option value="" disabled>Selecione o modelo…</option>
            @for (o of opcoes(form.controls.modeloModeracao.value); track o.id) {
              <option [value]="o.id" [disabled]="o.inativo">{{ o.rotulo }}</option>
            }
          </select>
        </div>
        <div class="form-field">
          <label class="label" for="ia-mod-chat">Modelo — Chat <span class="req" aria-hidden="true">*</span></label>
          <select id="ia-mod-chat" class="input" [formControl]="form.controls.modeloChat"
            [class.input--error]="invalido('modeloChat')">
            <option value="" disabled>Selecione o modelo…</option>
            @for (o of opcoes(form.controls.modeloChat.value); track o.id) {
              <option [value]="o.id" [disabled]="o.inativo">{{ o.rotulo }}</option>
            }
          </select>
        </div>
        <div class="form-field">
          <label class="label" for="ia-mod-prontuario">Modelo — Prontuário <span class="req" aria-hidden="true">*</span></label>
          <select id="ia-mod-prontuario" class="input" [formControl]="form.controls.modeloProntuario"
            [class.input--error]="invalido('modeloProntuario')">
            <option value="" disabled>Selecione o modelo…</option>
            @for (o of opcoes(form.controls.modeloProntuario.value); track o.id) {
              <option [value]="o.id" [disabled]="o.inativo">{{ o.rotulo }}</option>
            }
          </select>
        </div>
      </div>
      <span class="field-hint">O preço (US$/MTok de entrada / saída) vem do cadastro Modelos de IA e é o que calcula o custo de uso.</span>

      @if (avisoPdfProntuario(); as aviso) {
        <div class="ia-pdf-aviso" role="alert">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 9v4M12 17h.01" /><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
          </svg>
          <span>{{ aviso }}</span>
        </div>
      }

      <label class="ia-check">
        <input type="checkbox" [formControl]="form.controls.ativo" />
        <span>Ativo (quando desligado, a IA deste escopo fica indisponível)</span>
      </label>

      @if (escopo().tipo === 'unidade' && configurado()) {
        <div class="ia-padrao-acao">
          @if (confirmandoRemocao()) {
            <span class="ia-padrao-acao__pergunta">
              Remover a credencial própria desta unidade? Ela passará a usar o padrão da clínica.
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
              Usar a credencial padrão da clínica
            </button>
            <span class="field-hint">Limpa os campos e remove a credencial própria desta unidade.</span>
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
          {{ testando() ? 'Testando…' : 'Testar credencial' }}
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
    .ia-sem-modelos { margin-top: 0.9rem; padding: 0.7rem 0.95rem; border-radius: 0.55rem; font-size: 0.9rem;
      background: #fdf3e2; color: #8a5a00; border: 1px solid #f2d9a8; }
    .ia-pdf-aviso { display: flex; gap: 0.55rem; align-items: flex-start; margin-top: 0.6rem; padding: 0.7rem 0.95rem;
      border-radius: 0.55rem; font-size: 0.88rem; background: #fdf3e2; color: #8a5a00; border: 1px solid #f2d9a8; }
    .ia-pdf-aviso svg { width: 1.1rem; height: 1.1rem; flex: 0 0 auto; margin-top: 0.05rem; }
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
  private readonly modeloService = inject(ModeloIaService);
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

  /** Provedores oferecidos (suportados + com modelo ativo), vindos do backend. */
  protected readonly provedores = signal<string[]>([]);
  /** Modelos ativos do cadastro "Modelos de IA" DO PROVEDOR escolhido — alimentam os selects. */
  protected readonly modelos = signal<ModeloIa[]>([]);
  protected readonly semModelos = computed(() => this.modelos().length === 0);

  protected readonly usandoPadrao = computed(() => this.escopo().tipo === 'unidade' && !this.configurado());

  /** Id do modelo escolhido para o Prontuário — rastreado para avisar quando não aceitar PDF. */
  protected readonly modeloProntuarioSel = signal('');
  /**
   * Aviso quando o modelo de PRONTUÁRIO escolhido não aceita PDF: a análise de documento manda o PDF
   * nativo, então um modelo sem suporte a PDF não conseguiria analisar esses documentos (imagens, sim).
   */
  protected readonly avisoPdfProntuario = computed(() => {
    const id = this.modeloProntuarioSel();
    if (!id) return null;
    const m = this.modelos().find((x) => x.modeloId === id);
    if (!m || m.suportaPdf !== false) return null;
    return `O modelo de prontuário "${m.nome}" não aceita PDF — documentos em PDF não serão analisados por ele `
      + `(imagens continuam funcionando). Para analisar PDFs, escolha um modelo com suporte a PDF.`;
  });

  /** Enquanto aplicamos o status carregado, não reagir ao valueChanges do provedor (que limparia os modelos). */
  private aplicandoStatus = false;

  protected readonly form = new FormGroup({
    provedor: new FormControl('ANTHROPIC', { nonNullable: true, validators: [Validators.required] }),
    apiKey: new FormControl('', { nonNullable: true }),
    baseUrl: new FormControl('', { nonNullable: true }),
    modeloModeracao: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    modeloChat: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    modeloProntuario: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  constructor() {
    this.carregarProvedores();
    // Troca manual de provedor: zera os modelos escolhidos e recarrega os ativos do novo provedor.
    this.form.controls.provedor.valueChanges.subscribe((prov) => {
      if (this.aplicandoStatus) return;
      this.form.controls.modeloModeracao.setValue('');
      this.form.controls.modeloChat.setValue('');
      this.form.controls.modeloProntuario.setValue('');
      this.carregarModelos(prov);
    });
    // Rastreia o modelo de prontuário escolhido para o aviso de PDF.
    this.form.controls.modeloProntuario.valueChanges.subscribe((v) => this.modeloProntuarioSel.set(v ?? ''));
    effect(() => {
      const escopo = this.escopo();
      this.carregar(escopo);
    });
  }

  private carregarProvedores(): void {
    this.service.provedores().subscribe({
      next: (p) => this.provedores.set(p?.length ? p : ['ANTHROPIC']),
      error: () => this.provedores.set(['ANTHROPIC']),
    });
  }

  private carregarModelos(provedor: string): void {
    this.modeloService.ativos(provedor).subscribe({
      next: (m) => this.modelos.set(m),
      error: () => this.modelos.set([]),
    });
  }

  /** Opções do select de provedor — garante que o provedor salvo apareça mesmo se o backend não o listar. */
  protected opcoesProvedor(): { id: string; rotulo: string }[] {
    const lista = (this.provedores().length ? this.provedores() : ['ANTHROPIC']).slice();
    const atual = this.form.controls.provedor.value;
    if (atual && !lista.includes(atual)) lista.unshift(atual);
    return lista.map((id) => ({ id, rotulo: this.rotuloProvedor(id) }));
  }

  /** Nome amigável do provedor para rótulos/placeholder. */
  protected rotuloProvedor(id: string): string {
    switch (id) {
      case 'ANTHROPIC': return 'Anthropic · Claude';
      case 'OPENAI': return 'OpenAI · GPT';
      case 'GOOGLE_GEMINI': return 'Google · Gemini';
      case 'AZURE_OPENAI': return 'Azure OpenAI';
      default: return id;
    }
  }

  /** Placeholder da chave conforme o provedor. */
  protected placeholderChave(): string {
    switch (this.form.controls.provedor.value) {
      case 'ANTHROPIC': return 'sk-ant-...';
      case 'OPENAI': return 'sk-...';
      case 'GOOGLE_GEMINI': return 'AIza...';
      case 'AZURE_OPENAI': return 'chave do recurso Azure';
      default: return 'chave da API';
    }
  }

  /**
   * Opções do select para o valor atual do controle. Se o valor salvo não estiver entre os ativos
   * (ex.: o modelo foi desativado depois), ele entra como opção desabilitada marcada "(indisponível)"
   * para o usuário ver o que está gravado e trocar por um ativo.
   */
  protected opcoes(valorAtual: string): { id: string; rotulo: string; inativo: boolean }[] {
    const lista = this.modelos().map((m) => ({ id: m.modeloId, rotulo: this.rotulo(m), inativo: false }));
    if (valorAtual && !lista.some((o) => o.id === valorAtual)) {
      lista.unshift({ id: valorAtual, rotulo: `${valorAtual} (indisponível)`, inativo: true });
    }
    return lista;
  }

  private rotulo(m: ModeloIa): string {
    return `${m.nome} · US$ ${this.preco(m.precoEntradaUsdMtok)} / ${this.preco(m.precoSaidaUsdMtok)} por MTok`;
  }

  private preco(v: number): string {
    return (v ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
  }

  protected invalido(campo: 'modeloModeracao' | 'modeloChat' | 'modeloProntuario'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
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
    const provedor = s.provedor || 'ANTHROPIC';
    this.aplicandoStatus = true;
    this.form.reset({
      provedor,
      apiKey: '',
      baseUrl: s.baseUrl ?? '',
      modeloModeracao: s.modeloModeracao ?? '',
      modeloChat: s.modeloChat ?? '',
      modeloProntuario: s.modeloProntuario ?? '',
      ativo: s.configurado ? s.ativo : true,
    });
    this.aplicandoStatus = false;
    this.modeloProntuarioSel.set(s.modeloProntuario ?? '');
    this.carregarModelos(provedor);
    this.apiKeyPreenchida.set(s.apiKeyPreenchida);
    this.configurado.set(s.configurado);
    this.erroApiKey.set(false);
  }

  private valores(): FormIa {
    const v = this.form.getRawValue();
    const baseUrl = v.baseUrl.trim();
    return {
      provedor: v.provedor,
      apiKey: v.apiKey, // em branco mantém a atual
      baseUrl: baseUrl ? baseUrl : null,
      modeloModeracao: v.modeloModeracao.trim(),
      modeloChat: v.modeloChat.trim(),
      modeloProntuario: v.modeloProntuario.trim(),
      ativo: v.ativo,
    };
  }

  protected salvar(): void {
    // API key obrigatória só na PRIMEIRA vez (depois, em branco mantém a atual).
    const semApiKey = !this.apiKeyPreenchida() && !this.form.controls.apiKey.value.trim();
    const modelosInvalidos =
      this.form.controls.modeloModeracao.invalid ||
      this.form.controls.modeloChat.invalid ||
      this.form.controls.modeloProntuario.invalid;
    if (semApiKey || modelosInvalidos) {
      this.erroApiKey.set(semApiKey);
      this.form.controls.modeloModeracao.markAsTouched();
      this.form.controls.modeloChat.markAsTouched();
      this.form.controls.modeloProntuario.markAsTouched();
      this.toastr.error(semApiKey ? 'Informe a API key do provedor.' : 'Escolha os 3 modelos de IA.');
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

  /** Remove a credencial própria da unidade → limpa a tela e volta a usar o padrão da clínica. */
  protected confirmarUsarPadrao(): void {
    this.removendo.set(true);
    this.service.remover(this.escopo()).subscribe({
      next: (s) => {
        this.aplicarStatus(s);
        this.resultado.set(null);
        this.confirmandoRemocao.set(false);
        this.removendo.set(false);
        this.toastr.success('A unidade passou a usar a credencial de IA padrão da clínica.');
      },
      error: (e) => {
        this.removendo.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível remover a credencial própria da unidade.');
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
        this.resultado.set({ ok: false, mensagem: e?.error?.message ?? 'Falha ao testar a credencial.' });
        this.testando.set(false);
      },
    });
  }
}
