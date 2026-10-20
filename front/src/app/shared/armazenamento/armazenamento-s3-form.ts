import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { ArmazenamentoService } from './armazenamento.service';
import { EscopoS3Input, FormS3, ResultadoTesteS3, StatusS3 } from './armazenamento.model';

/**
 * Formulário (reutilizável) do S3 de um escopo (unidade ou padrão do inquilino). Carrega o status,
 * salva (secret-key WRITE-ONLY — em branco mantém a atual) e testa a conexão. Salva por conta própria
 * (independente do form que o hospeda).
 *
 * <p>Para UNIDADE, a config é OPCIONAL: sem config própria, a unidade usa o S3 padrão do inquilino.
 * O botão "Usar o S3 padrão da clínica" remove o S3 próprio da unidade (limpa a tela) e deixa isso
 * explícito com um aviso.
 */
@Component({
  selector: 'app-armazenamento-s3-form',
  imports: [ReactiveFormsModule],
  template: `
    @if (carregando()) {
      <div class="state"><span class="spinner" aria-hidden="true"></span><p>Carregando armazenamento…</p></div>
    } @else {
      <p class="field-hint" style="margin-bottom: 1rem">
        {{ escopo().tipo === 'unidade'
          ? 'S3 desta unidade — os arquivos dela (documentos de prontuário, imagens de postagem, SIRESP, termos) são guardados aqui. Deixe em branco para usar o S3 padrão da clínica.'
          : 'S3 padrão da clínica — guarda os arquivos compartilhados entre unidades: fotos de pacientes e profissionais, modelos de TCLE e logomarca.' }}
      </p>

      @if (usandoPadrao()) {
        <div class="s3-padrao" role="status">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          <div>
            <b>Esta unidade usa o S3 padrão da clínica.</b>
            <span>Ela não tem armazenamento próprio. Preencha os campos abaixo e salve para dar a ela um S3 só dela.</span>
          </div>
        </div>
      }

      <div class="form-grid">
        <div class="form-field">
          <label class="label" for="s3-public">Endpoint público <span class="req" aria-hidden="true">*</span></label>
          <input id="s3-public" class="input" [formControl]="form.controls.publicEndpoint"
            placeholder="https://s3.exemplo.com" autocomplete="off"
            [class.input--error]="invalido('publicEndpoint')" />
          <span class="field-hint">URL que o navegador acessa (nas URLs pré-assinadas).</span>
        </div>
        <div class="form-field">
          <label class="label" for="s3-internal">Endpoint interno</label>
          <input id="s3-internal" class="input" [formControl]="form.controls.internalEndpoint"
            placeholder="(igual ao público, se vazio)" autocomplete="off" />
          <span class="field-hint">Usado pelo servidor (excluir/baixar). Em branco, usa o público.</span>
        </div>
        <div class="form-field">
          <label class="label" for="s3-bucket">Bucket <span class="req" aria-hidden="true">*</span></label>
          <input id="s3-bucket" class="input" [formControl]="form.controls.bucket"
            placeholder="meu-bucket" autocomplete="off" [class.input--error]="invalido('bucket')" />
        </div>
        <div class="form-field">
          <label class="label" for="s3-region">Região</label>
          <input id="s3-region" class="input" [formControl]="form.controls.region"
            placeholder="us-east-1" autocomplete="off" />
        </div>
        <div class="form-field">
          <label class="label" for="s3-access">Access Key <span class="req" aria-hidden="true">*</span></label>
          <input id="s3-access" class="input" [formControl]="form.controls.accessKey"
            autocomplete="off" [class.input--error]="invalido('accessKey')" />
        </div>
        <div class="form-field">
          <label class="label" for="s3-secret">Secret Key</label>
          <input id="s3-secret" type="password" class="input" [formControl]="form.controls.secretKey"
            [placeholder]="secretPreenchido() ? '•••••••• (mantém a atual)' : 'Secret Key'"
            autocomplete="new-password" />
          <span class="field-hint">
            {{ secretPreenchido() ? 'Já salva (cifrada). Preencha só para trocar.' : 'Guardada cifrada no banco.' }}
          </span>
        </div>
      </div>

      <label class="s3-check">
        <input type="checkbox" [formControl]="form.controls.ativo" />
        <span>Ativo (quando desligado, os uploads deste escopo são bloqueados)</span>
      </label>

      @if (escopo().tipo === 'unidade' && configurado()) {
        <div class="s3-padrao-acao">
          @if (confirmandoRemocao()) {
            <span class="s3-padrao-acao__pergunta">
              Remover o S3 próprio desta unidade? Ela passará a usar o S3 padrão da clínica.
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
              Usar o S3 padrão da clínica
            </button>
            <span class="field-hint">Limpa os campos e remove o S3 próprio desta unidade.</span>
          }
        </div>
      }

      @if (resultado(); as r) {
        <div class="s3-teste" [class.s3-teste--ok]="r.ok" [class.s3-teste--erro]="!r.ok" role="status">
          {{ r.mensagem }}
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn btn--ghost" (click)="testar()" [disabled]="testando() || salvando()">
          {{ testando() ? 'Testando…' : 'Testar conexão' }}
        </button>
        <button type="button" class="btn btn--solid" (click)="salvar()" [disabled]="salvando() || testando()">
          {{ salvando() ? 'Salvando…' : 'Salvar armazenamento' }}
        </button>
      </div>
    }
  `,
  styles: [`
    .s3-check { display: flex; align-items: center; gap: 0.5rem; margin: 1rem 0 0.25rem; font-size: 0.9rem; color: var(--ink); }
    .s3-check input { width: 1rem; height: 1rem; }
    .s3-teste { margin-top: 1rem; padding: 0.7rem 0.95rem; border-radius: 0.55rem; font-size: 0.9rem; }
    .s3-teste--ok { background: #e2f4ec; color: #1e6f54; }
    .s3-teste--erro { background: #fdecea; color: #b42318; }
    .s3-padrao { display: flex; gap: 0.6rem; align-items: flex-start; margin: 0 0 1.1rem;
      padding: 0.75rem 0.9rem; border-radius: 0.55rem; background: #e2f4ec; color: #1e6f54; border: 1px solid #bfe4d3; }
    .s3-padrao svg { width: 1.15rem; height: 1.15rem; flex: 0 0 auto; margin-top: 0.1rem; }
    .s3-padrao b { display: block; }
    .s3-padrao span { font-size: 0.88rem; opacity: 0.92; }
    .s3-padrao-acao { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; margin: 0.85rem 0 0.25rem; }
    .s3-padrao-acao__pergunta { font-size: 0.9rem; color: var(--ink); }
    .btn--perigo { color: #b42318; border-color: #f2c4bd; }
  `],
})
export class ArmazenamentoS3Form {
  private readonly service = inject(ArmazenamentoService);
  private readonly toastr = inject(ToastrService);

  readonly escopo = input.required<EscopoS3Input>();

  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly testando = signal(false);
  protected readonly removendo = signal(false);
  protected readonly confirmandoRemocao = signal(false);
  protected readonly secretPreenchido = signal(false);
  /** A unidade/inquilino tem config PRÓPRIA salva? (false p/ unidade = herda o padrão do inquilino). */
  protected readonly configurado = signal(false);
  protected readonly resultado = signal<ResultadoTesteS3 | null>(null);

  /** Unidade sem config própria → usa o S3 padrão da clínica (mostra o aviso). */
  protected readonly usandoPadrao = computed(() => this.escopo().tipo === 'unidade' && !this.configurado());

  protected readonly form = new FormGroup({
    publicEndpoint: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    internalEndpoint: new FormControl('', { nonNullable: true }),
    bucket: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    region: new FormControl('us-east-1', { nonNullable: true }),
    accessKey: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    secretKey: new FormControl('', { nonNullable: true }),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  constructor() {
    // Recarrega o status sempre que o escopo muda.
    effect(() => {
      const escopo = this.escopo();
      this.carregar(escopo);
    });
  }

  private carregar(escopo: EscopoS3Input): void {
    this.carregando.set(true);
    this.resultado.set(null);
    this.confirmandoRemocao.set(false);
    this.service.status(escopo).subscribe({
      next: (s) => {
        this.aplicarStatus(s);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  /** Preenche o form a partir do status salvo (vazio = sem config própria). */
  private aplicarStatus(s: StatusS3): void {
    this.form.reset({
      publicEndpoint: s.publicEndpoint ?? '',
      internalEndpoint: s.internalEndpoint ?? '',
      bucket: s.bucket ?? '',
      region: s.region || 'us-east-1',
      accessKey: s.accessKey ?? '',
      secretKey: '',
      ativo: s.configurado ? s.ativo : true,
    });
    this.secretPreenchido.set(s.secretPreenchido);
    this.configurado.set(s.configurado);
  }

  protected invalido(campo: 'publicEndpoint' | 'bucket' | 'accessKey'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
  }

  private valores(): FormS3 {
    const v = this.form.getRawValue();
    return {
      publicEndpoint: v.publicEndpoint.trim(),
      internalEndpoint: v.internalEndpoint.trim(),
      bucket: v.bucket.trim(),
      region: v.region.trim(),
      accessKey: v.accessKey.trim(),
      secretKey: v.secretKey, // em branco mantém a atual
      ativo: v.ativo,
    };
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    this.resultado.set(null);
    this.service.salvar(this.escopo(), this.valores()).subscribe({
      next: (s) => {
        this.secretPreenchido.set(s.secretPreenchido);
        this.configurado.set(s.configurado);
        this.form.controls.secretKey.reset('');
        this.form.markAsPristine();
        this.salvando.set(false);
        this.toastr.success('Armazenamento salvo.');
        this.testar(); // já testa a conexão após salvar (feedback imediato)
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(e?.error?.message ?? e?.error?.detail ?? 'Não foi possível salvar o armazenamento.');
      },
    });
  }

  /** Remove o S3 próprio da unidade → limpa a tela e volta a usar o S3 padrão da clínica. */
  protected confirmarUsarPadrao(): void {
    this.removendo.set(true);
    this.service.remover(this.escopo()).subscribe({
      next: (s) => {
        this.aplicarStatus(s);
        this.resultado.set(null);
        this.confirmandoRemocao.set(false);
        this.removendo.set(false);
        this.toastr.success('A unidade passou a usar o S3 padrão da clínica.');
      },
      error: (e) => {
        this.removendo.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível remover o S3 próprio da unidade.');
      },
    });
  }

  protected testar(): void {
    this.testando.set(true);
    this.resultado.set(null);
    // Testa os VALORES DA TELA (sem precisar salvar). Secret em branco → o backend usa a salva.
    this.service.testar(this.escopo(), this.valores()).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.testando.set(false);
      },
      error: (e) => {
        this.resultado.set({ ok: false, mensagem: e?.error?.message ?? 'Falha ao testar a conexão.' });
        this.testando.set(false);
      },
    });
  }
}
